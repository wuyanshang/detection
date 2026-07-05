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

---

## 10. 用数据决定：难例成因分类与方案选择（S4 前置）

> **重要前提**：本节写在"判定层已改为 `min-score=0` + 三态 `matchType`（EXACT/FALLBACK/UNMATCHED）"之后。这个改动已经把上文 §4.1 里"召回空 / category=null"两类触发**大部分消化掉了**（永远有候选、无明确命中时挑最接近的真实叶子）。因此**是否值得上 loop，不能拍脑袋，要先按"难例成因"用数据分桶**——不同成因对应完全不同的解法，loop 只治其中一类。

### 10.1 难例成因（4 类）

| 代号 | 成因 | 典型表现 | 唯一有效的解法 |
|------|------|----------|----------------|
| **C1** | 检索词质量差（**可改写救回**） | 原始/多字段检索召不到 gold，但改写后能召到 | **loop 的 query 改写 / example-only**（loop 真正的适用区） |
| **C2** | 判定错（**判定可救**） | gold 已进融合 topK，但 judge 选错 / 误判 FALLBACK | 改判定 prompt / 澄清动作（loop 澄清有一定用） |
| **C3** | 覆盖缺口 / 语义不可达（**loop 救不了**） | 全量池（min-score=0 + 大 topK）里 gold 仍进不了候选，或附录A 根本没有对应叶子 | **扩目录**（加自定义叶子）或 **人工复核队列** |
| **C4** | 粒度塌缩（**区分无意义**） | 多个语义不同字段的 gold 落到同一粗桶且安全级别相同（本数据里"营销员/中介机构代理人"塌缩到 `渠道管理信息`） | 只要安全级别→可接受；要细分→只能扩目录 |

> 经验判断：本数据（营销员/代理人/中介机构，附录A 无对应细分树）的难例，**大概率以 C3 + C4 为主**，C1 次之，C2 少。若真如此，则**loop 不是主要杠杆，扩目录 / 复核队列才是**。但结论必须用下面的评测确认，不要靠猜。

### 10.2 怎么把成因从评测里"读"出来（每行贴标签）

复用 `eval/recall_eval.py`，用标注集（每行含 `expected_category` = 附录A 金标）跑：

```bash
python recall_eval.py --data 标注集.xlsx --strategy all --judge --min-score 0 --topk 30 --k 30 --out hardcase
```

产出各策略的 `hardcase_{baseline,multifield,rewrite}_detail.csv`（含每行 `recall_rank` / `judged`）。**按行号跨策略 join**，用如下规则给每个"判错/未命中"的样本贴成因标签：

```text
对每一行:
  hitAny  = 任一策略 recall_rank 非空（gold 进过融合 topK）
  hitBase = baseline recall_rank 非空
  hitRw   = rewrite  recall_rank 非空
  judgedOK= 该行 judged == gold

  if 端到端已判对(judgedOK 且 hitBase): 非难例, 跳过
  elif not hitAny:                      成因 = C3   # 全策略全量池都召不到 → 不可达/覆盖缺口
  elif (not hitBase) and hitRw:         成因 = C1   # 改写能救 → loop query 改写有效
  elif hitAny and not judgedOK:         成因 = C2   # 召回到了但判错 → 判定问题
  else:                                 成因 = 其他/边界, 人工看

# C4 单独统计（与上面正交）:
#   对每个 gold category, 统计命中它的"语义不同字段"个数; 
#   高频粗桶(如 渠道管理信息) 且 组内安全级别一致 → 该组样本打 C4 标记
```

> 说明：这里不需要改 `recall_eval.py` 主逻辑，一个几十行的后处理脚本（读三份 detail.csv + 金标里各 category 的安全级别）就能算出 C1/C2/C3/C4 分布。需要的话我可以补 `eval/hardcase_diagnose.py`。

同时，**新链路（classify2）跑一遍标注集**，统计三态占比与 FALLBACK 质量：
- `matchType` 分布：EXACT / FALLBACK / UNMATCHED 各占比；
- **FALLBACK 正确率**：FALLBACK 项里 `category == gold` 的比例（低→说明兜底在乱挑，要么收紧、要么这些本就是 C3）。

### 10.3 决策规则（用你的标注集回填阈值）

| 数据情况（成因分布） | 建议方案 | 理由 |
|----------------------|----------|------|
| **C1 占难例 ≳ 30%** | **上 loop**，但只保留 §4.2 的 **query 改写 + example-only** 两个动作，砍掉"放宽 min-score/topK"（300 条池已见底，放宽是空转） | 这些难例"再改写检索一次"确实能救回 |
| **C2 占比高** | **先改判定**：判定 prompt 补规则 / 加 top2 澄清（§4.2 动作3），**不必上整套 loop** | 召回没问题，问题在判定，单点修更便宜 |
| **C3 + C4 是难例主体** | **扩目录 + 人工复核队列**，loop **不做** | 目录里没有的叶子，改写/重试都召不回来；loop 花 token 也只能落回粗桶 |
| **FALLBACK 正确率高、UNMATCHED 很少** | 维持现状（min-score=0 + FALLBACK），只加复核队列兜低置信项 | 主链路已够用，loop 边际收益低 |

### 10.4 三种方案的适用边界（一句话版）

- **难例 loop（S4）**：治 **C1**（+部分 C2）；**不治 C3/C4**。仅当 C1 占比可观才值得，且要按新 `matchType` 改触发条件（触发 UNMATCHED / 低相关度 FALLBACK，而非旧的"null / 召回空"）。
- **扩目录**：治 **C3/C4**，一次性成本，**最契合本数据领域**（附录A 是资料性附录，标准明确允许机构自建细分，给"营销员/代理人/中介机构"补几个叶子即可）。
- **人工复核队列**：兜 **C3 残留 + 高风险 FALLBACK**，把低置信项交给人；治标但保质量，配合前两者用。

### 10.5 落地顺序建议

1. 先把标注集在**新链路 + `recall_eval`** 上各跑一遍，产出 §10.2 的成因分布 + 三态占比 + FALLBACK 正确率。
2. 按 §10.3 的分布对号入座选方案——**大概率是"扩目录 + 复核队列"优先，loop 靠后**。
3. 只有当 C1 被证明占比可观时，才按 §3–§7 落地 loop，并同步把触发条件改成基于 `matchType`。
