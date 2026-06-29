# 召回评测脚本 (recall-eval)

在安全分类分级功能完整落地前，用标注数据**独立量化召回与判定质量**，用真实数据决定优化方向（召回侧 vs 判定侧）。脚本与 Spring 实现解耦，可单独运行。

对应设计文档：`../安全分类分级接口.txt` 第 13、14 节。

## 它回答什么问题

把流程拆成两段独立能力分别度量：

| 指标 | 含义 | 说明什么 |
|------|------|----------|
| `recall@K` | 正确 category 是否出现在融合后 top-K | **召回**是否是瓶颈 |
| `vector_recall@K` / `bm25_recall@K` | 两路各自命中率 | 哪一路弱 |
| `MRR` | 正确项的平均倒数排名 | 召回排序质量 |
| `judge_acc`（召回命中子集） | 候选含正确答案时 LLM 选对比例 | **判定**是否是瓶颈 |
| `end2end_acc` | 端到端选对比例 | 总效果 |

**决策逻辑**：若 `recall@K` 低 → 砸资源在召回（文档 13.1 方案A / 13.2 方案B），自检无用；若 recall 高但 `judge_acc` 低 → 才考虑判定侧优化。

## 安装

```bash
pip install -r requirements.txt
```

## 配置（环境变量）

```bash
# Windows PowerShell
$env:ES_HOST="http://127.0.0.1:9200"
$env:ES_USERNAME="elastic"          # 可选
$env:ES_PASSWORD="xxxx"             # 可选
$env:ES_INDEX="safe_all_topic"      # 默认
$env:DASHSCOPE_API_KEY="sk-xxxx"    # embedding 与 LLM 必需
# 可选：EMBED_MODEL(text-embedding-v4) / EMBED_DIM(1024) / LLM_MODEL(qwen-plus)
```

```bash
# Linux / macOS
export ES_HOST=http://127.0.0.1:9200
export DASHSCOPE_API_KEY=sk-xxxx
```

## 准备标注数据

CSV 或 XLSX，必须包含表头，列名如下（`expected_category` 为人工标注的金标）：

| 列 | 必填 | 说明 |
|----|------|------|
| systemName / systemDesc | 否 | 系统名 / 描述 |
| tableName / tableChnName | 否 | 表英文名 / 中文名 |
| columnName | 否 | 字段英文名（缩写场景的关键） |
| columnChnName | 是 | 字段中文名 |
| expected_category | 是 | 正确的完整 category（`|` 分隔，须与 ES 中一致） |

参考 `labeled_sample.csv`。建议标注 100~300 条覆盖典型场景（尤其“信息在英文名/表名”的难例）。

## 运行

```bash
# 三种召回策略横向对比（baseline / multifield / rewrite）
python recall_eval.py --data labeled_sample.csv --strategy all

# 只测多字段策略，并额外跑 LLM 判定
python recall_eval.py --data labeled_sample.csv --strategy multifield --judge

# 先小规模试跑
python recall_eval.py --data labeled_sample.csv --limit 20
```

常用参数：

| 参数 | 默认 | 说明 |
|------|------|------|
| `--strategy` | all | baseline / multifield / rewrite / all |
| `--k` | 10 | 融合后 recall@K 的 K |
| `--topk` | 10 | 每路检索 top-K |
| `--min-score` | 0.0 | 向量检索 min_score；**评测期建议设 0**，先看全量召回上限 |
| `--judge` | 关 | 跑 LLM 判定，统计判定/端到端准确率（耗 token） |
| `--limit` | 0 | 只测前 N 条 |
| `--out` | report | 报告输出前缀 |

## 三种召回策略

- **baseline**：仅用 `columnChnName`（当前设计的做法，作为基线）。
- **multifield**：`tableChnName + columnChnName + columnName` 拼接后检索（文档 13.1 手段①）。
- **rewrite**：调 LLM 把零散字段归一为规范检索词，展开英文缩写（文档 13.1 手段③）。

每种策略都做 向量 + BM25 双路检索，再 RRF 融合。

## 输出

- `report_summary.json`：各策略指标汇总。
- `report_<strategy>_detail.csv`：每条样本的 query、召回命中排名、两路是否命中、top1、判定结果，便于人工定位 bad case。

## 注意

- `expected_category` 必须与 ES 索引里的 `category` 字符串完全一致（脚本只做空白归一化与全角竖线`｜`→`|`），否则会误判为未命中。
- 评测向量召回上限时把 `--min-score` 设为 0，避免阈值过早裁掉候选造成 recall 失真；线上阈值另行调优。
