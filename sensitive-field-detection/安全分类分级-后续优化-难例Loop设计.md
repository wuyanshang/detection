# 安全分类分级 — 后续优化：难例受控 Loop 设计（S4）

> 关联：`安全分类分级-召回优化重构方案.md` 第 9 节 S4。本文件为**后续优化项**的详细设计，首版 v2（`classify2` 模块）尚未实现，默认关闭。
> 一句话定位：主链路一次跑完覆盖绝大多数字段；只有"判不准"的**难例**才触发一个**有边界的反思-重试循环**，把 token 花在刀刃上。

---

## 1. 为什么要 loop（问题）

v2 主链路（表级推断 → content 入库混合召回 → 满上下文判定 + 主体一致性）已能解决大多数字段，但仍有一批"难例"一次判不准：

- 召回全空 / top 候选与字段语义都对不上（判 `null`）；
- 近义兄弟分差极小（"投保人姓名 vs 客户姓名 vs 员工姓名"）；
- 判定主体与表级推断主体冲突；
- 字段中文名质量太差、英文缩写生僻，一次改写没到位。

对这些难例，**再检索一次、换个说法、或拿定义澄清一下**往往就能救回来。loop 就是把这个"再试一次"做成受控机制。

## 2. 设计原则

- **只兜难例**：正常字段零额外开销（判定后门控直接放行）。
- **动作有限可枚举**：不是自由放飞的 Agent，而是固定的几种动作，保证可复现、可回归、成本可控。
- **轮数有上界**：`maxRounds` 默认 2~3，防死循环。
- **纯增量、可灰度**：新增节点 + 条件边 + 回边，`enabled=false` 默认关，不影响现有 v2 链路。

## 3. 编排（挂在 v2 图的 judge 之后）

```
queryPrep →(vectorSearch ∥ bm25Search)→ mergeDedup → judge
                                                        │
                                            [条件边: isHardCase?]
                                       ┌────────────────┴────────────────┐
                                  否/已解决/超轮数                    是且轮数未满
                                       ↓                                ↓
                                      END                          reflect(反思)
                                                                      │ 产出新 query / 放宽参数
                                                                      └──回边──> vectorSearch
```

- `reflect` 产出新的检索词/检索参数后跳回 `vectorSearch`（∥ `bm25Search`），重新 `mergeDedup` → `judge`，形成闭环。
- Spring AI Alibaba Graph 用 `addConditionalEdges("judge", ...)` 实现条件路由，回边用 `addEdge("reflect","vectorSearch")`。

## 4. 三个要素

### 4.1 触发条件（judge 后门控，任一命中才进 loop）

| 触发类型 | 判据 | 对应动作倾向 |
|----------|------|--------------|
| 未匹配 | judge 返回 `category=null` | 放宽检索 / 改写 query |
| 近义难分 | top1 与 top2 融合分差 < `scoreGapThreshold` | 澄清判定 |
| 主体冲突 | judge 主体 ≠ 表级推断主体 | 按主体改写 query |
| 召回空 | 候选数 = 0 | 放宽检索 / example-only 检索 |

均不命中 → 直接 END，正常字段无额外调用。

### 4.2 动作空间（有限、可枚举 —— "受控"的关键）

1. **改写 query**：换主体表述、展开英文缩写、借表上下文补全（"姓名"→"投保人姓名"）、加同义词。
2. **放宽检索**：调低 `min-score`、增大 `topK`、或**只用 example 文档**再捞一遍（专治召回空）。
3. **澄清判定**：把 top2 候选的 `content` + 三级定义单独拎出来做"二选一"澄清（专治近义兄弟）。
4. **降层兜底（可选）**：四级定不了时退一层，先定三级子树，再在子树内选四级。

### 4.3 终止条件

- **已解决**：`category != null` 且主体一致（且分差达标）→ END；
- **超轮数**：`loopRound >= maxRounds` → 停，输出当前最优或 `null`；
- **明确无匹配**：动作用尽仍判 null → END（null）。

## 5. reflect 节点：两种实现（二选一）

### 5.1 规则驱动（推荐先做）
按失败类型用代码直接决定下一步：
- `null` / 召回空 → 放宽检索（降 min-score、example-only）；
- 分差小 → 澄清判定（top2 二选一）；
- 主体冲突 → 用表级主体重写 query 再检索。

优点：**可复现、好回归、便宜**（多数动作不额外调 LLM）。

### 5.2 LLM 驱动（ReAct 式）
`reflect` 调一次 LLM，让它根据当前字段 + 候选 + 失败原因，自己输出「下一步动作 + 新 query」。
优点：灵活，能处理没预料到的情况；缺点：慢、不确定、难回归。

> 建议：先上 5.1 规则驱动；若残留难例仍多且模式复杂，再考虑 5.2。

## 6. 需要新增/改动的东西

**新增节点**
- `ReflectNode`（`classify2/workflow/node/`）：读 `result` + 触发原因，产出新 `queryTerms` / 检索放宽标志 / 澄清模式标志，`loopRound + 1` 回写。

**条件路由**
- `judge` 后加条件边：`isHardCase && loopRound < maxRounds` → `reflect`；否则 → END。

**state 新增字段**（`FieldContext`）
- `loopRound`(int)、`lastAction`(String)、`relaxRetrieve`(boolean)、`exampleOnly`(boolean)、`clarifyMode`(boolean)。

**检索节点小改**（`VectorSearchV2Node` / `Bm25SearchV2Node`）
- 读 `relaxRetrieve` / `exampleOnly`：放宽时用更低 min-score、更大 topK；example-only 时 BM25/向量只针对 example 文档（可加 `doc_type=example` 过滤）。

**配置**（`classify-v2.hard-case-loop`）
```yaml
classify-v2:
  hard-case-loop:
    enabled: false          # 默认关，灰度再开
    max-rounds: 3
    score-gap-threshold: 0.05
    relax-min-score: 0.3    # 放宽检索时的 min-score
```

## 7. 伪代码（规则驱动）

```text
after judge:
  reason = detectHardCase(ctx)          # null / 近义 / 主体冲突 / 召回空 / none
  if reason == none or ctx.loopRound >= maxRounds:
      END
  else:
      ReflectNode:
          switch reason:
            召回空/null   -> ctx.relaxRetrieve=true; 若已放宽过 -> ctx.exampleOnly=true
            近义难分      -> ctx.clarifyMode=true (judge 只对 top2 用 content 澄清)
            主体冲突      -> ctx.queryTerms = rewriteBySubject(ctx.tableSubject, ctx.columnChnName)
          ctx.loopRound += 1
          goto vectorSearch
```

## 8. 评测（复用 eval/recall_eval.py 思路，单列统计）

- **触发率**：多少比例字段进了 loop（越低越好，说明主链路强）。
- **纠正率**：进 loop 的字段里，loop 后从错→对 / null→命中的比例。
- **额外成本**：平均每难例多花的 LLM 调用数、耗时。
- **回归**：构造"近义兄弟"专项集（各种"姓名"），确认 loop 不引入新的误配。

决策：若"触发率 × 纠正率"带来的收益 > 额外成本，则线上开启；否则保持关闭。

## 9. 与全自主 Agent 的关系

loop 本质是**把"让 AI 自主迭代搜索"关进笼子**的版本：动作空间固定、轮数有上限、每轮可评测。既拿到迭代纠错的收益，又避开全自主 Agent「慢 / 贵 / 不可控 / 难回归」四个坑。适合本场景（目录固定、需批量、需可复现）。
