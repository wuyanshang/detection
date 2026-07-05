#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
难例成因诊断 (hardcase-diagnose)

用途：读取 recall_eval.py 产出的各策略明细 CSV，按"难例成因"给每个判错/未命中的样本
贴标签（C1/C2/C3/C4），输出成因分布，用数据回答"该上难例 loop 还是扩目录/复核队列"。

对应文档：安全分类分级-后续优化-难例Loop设计.md 第 10 节。

前置：先跑 recall_eval，产出 {prefix}_{strategy}_detail.csv（含 recall_rank / judged / gold）：
  python recall_eval.py --data 标注集.xlsx --strategy all --judge --min-score 0 --topk 30 --k 30 --out hardcase

成因定义（详见文档 10.1）：
  C1 检索词质量差（可改写救回）：baseline 召不到 gold，但 rewrite 能召到 → loop query 改写有效
  C2 判定错（判定可救）：gold 已进任一策略 topK，但没被判对 → 判定 prompt / 澄清有效
  C3 覆盖缺口 / 不可达：全策略全量池都召不到 gold → loop 无效，需扩目录 / 复核队列
  C4 粒度塌缩（正交统计）：多个不同字段 gold 落到同一粗桶 → 需扩目录才能细分

用法：
  python hardcase_diagnose.py --prefix hardcase
  python hardcase_diagnose.py --prefix hardcase --strategies baseline,multifield,rewrite --c4-min 5
"""
import argparse
import csv
import os
import sys
from collections import Counter, defaultdict


def load_detail(prefix, strategy):
    path = f"{prefix}_{strategy}_detail.csv"
    if not os.path.exists(path):
        return None
    with open(path, "r", encoding="utf-8-sig", newline="") as f:
        return {row.get("row"): row for row in csv.DictReader(f)}


def is_hit(detail_row):
    """recall_rank 非空即视为 gold 进过融合 topK。"""
    if not detail_row:
        return False
    rr = (detail_row.get("recall_rank") or "").strip()
    return rr not in ("", "0", "None")


def judged_ok(detail_row):
    if not detail_row:
        return False
    j = (detail_row.get("judged") or "").strip()
    g = (detail_row.get("gold") or "").strip()
    return bool(j) and j.lower() != "null" and j == g


def main():
    ap = argparse.ArgumentParser(description="难例成因诊断")
    ap.add_argument("--prefix", required=True, help="recall_eval 的 --out 前缀，如 hardcase")
    ap.add_argument("--strategies", default="baseline,multifield,rewrite",
                    help="参与诊断的策略（逗号分隔），缺失的文件会自动跳过")
    ap.add_argument("--c4-min", type=int, default=5,
                    help="C4 塌缩告警阈值：一个 gold 被多少个不同字段命中就算高频粗桶")
    ap.add_argument("--out", default="", help="可选：把逐行成因标签写到该 CSV")
    args = ap.parse_args()

    strategies = [s.strip() for s in args.strategies.split(",") if s.strip()]
    details = {s: load_detail(args.prefix, s) for s in strategies}
    details = {s: d for s, d in details.items() if d is not None}
    if not details:
        raise SystemExit(f"未找到任何明细 CSV（前缀={args.prefix}，策略={strategies}）")
    print(f"载入策略明细: {list(details.keys())}")

    # 以并集的行号为准
    all_rows = set()
    for d in details.values():
        all_rows.update(d.keys())

    causes = Counter()
    per_row = []
    gold_field_count = defaultdict(set)   # gold -> 命中它的不同 query(字段) 集合，用于 C4

    for row_id in sorted(all_rows, key=lambda x: int(x) if str(x).isdigit() else 0):
        base = details.get("baseline", {}).get(row_id)
        rw = details.get("rewrite", {}).get(row_id)
        anystrat = [details[s].get(row_id) for s in details if details[s].get(row_id)]

        gold = ""
        for r in anystrat:
            gold = (r.get("gold") or "").strip()
            if gold:
                break

        hit_any = any(is_hit(r) for r in anystrat)
        hit_base = is_hit(base)
        hit_rw = is_hit(rw)
        ok_any = any(judged_ok(r) for r in anystrat)

        # C4 素材：把 gold 与命中它的字段（用 query 近似）累计
        for r in anystrat:
            if is_hit(r) and gold:
                gold_field_count[gold].add((r.get("query") or "").strip())

        # 非难例：baseline 召回到且判对 → 主链路能搞定，跳过
        if hit_base and ok_any:
            cause = "OK"
        elif not hit_any:
            cause = "C3"                      # 全量池都召不到 → 不可达 / 覆盖缺口
        elif (not hit_base) and hit_rw:
            cause = "C1"                      # 改写能救 → loop query 改写有效
        elif hit_any and not ok_any:
            cause = "C2"                      # 召回到了但判错 → 判定问题
        else:
            cause = "OTHER"                   # 边界，建议人工看

        causes[cause] += 1
        per_row.append({"row": row_id, "gold": gold, "cause": cause,
                        "hit_base": hit_base, "hit_rewrite": hit_rw,
                        "hit_any": hit_any, "judged_ok": ok_any})

    # C4：高频粗桶（一个 gold 被 >= c4-min 个不同字段命中）
    c4 = {g: len(fields) for g, fields in gold_field_count.items() if len(fields) >= args.c4_min}

    total = sum(causes.values())
    hardcases = total - causes.get("OK", 0)
    print("\n" + "=" * 60)
    print("难例成因分布")
    print("=" * 60)
    print(f"  样本总数: {total}；主链路已搞定(OK): {causes.get('OK', 0)}；难例: {hardcases}")
    for c in ("C1", "C2", "C3", "OTHER"):
        n = causes.get(c, 0)
        pct = f"{100.0 * n / hardcases:.1f}%" if hardcases else "N/A"
        print(f"  {c:<6}: {n:>4}  占难例 {pct}")

    print("\n" + "-" * 60)
    print(f"C4 粒度塌缩告警（gold 被 >= {args.c4_min} 个不同字段命中）")
    print("-" * 60)
    if not c4:
        print("  （无）")
    for g, cnt in sorted(c4.items(), key=lambda kv: kv[1], reverse=True):
        print(f"  {cnt:>4} 个字段 → {g}")

    print("\n结论指引（详见文档 10.3）：")
    print("  C1 占难例≥~30%  → 值得上 loop（只做 query 改写 + example-only）")
    print("  C2 占比高       → 先改判定 prompt / 加澄清，不必整套 loop")
    print("  C3+C4 为主体    → 扩目录 + 复核队列，loop 不做")

    if args.out:
        keys = ["row", "gold", "cause", "hit_base", "hit_rewrite", "hit_any", "judged_ok"]
        with open(args.out, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=keys)
            w.writeheader()
            w.writerows(per_row)
        print(f"\n逐行成因已写入: {args.out}")


if __name__ == "__main__":
    main()
