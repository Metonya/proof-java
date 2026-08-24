#!/usr/bin/env python3
"""Scaffold a labeling CSV from a coverdict verdict JSON's findings array.

Per docs/M0-VALIDATION-MANIFEST.md labeling protocol: review all findings up
to 100 per repo/rule, otherwise a seeded deterministic sample of 100.
Usage: python scaffold_labels.py <verdict.json> <out.csv> [--seed N]
"""
import argparse
import csv
import json
import random
from collections import defaultdict


def main():
    p = argparse.ArgumentParser()
    p.add_argument("verdict_json")
    p.add_argument("out_csv")
    p.add_argument("--seed", type=int, default=42)
    args = p.parse_args()

    with open(args.verdict_json, encoding="utf-8") as f:
        doc = json.load(f)

    findings = doc.get("findings", [])
    by_rule = defaultdict(list)
    for finding in findings:
        by_rule[finding["rule"]].append(finding)

    sampled = []
    for rule in sorted(by_rule):
        pool = sorted(by_rule[rule], key=lambda x: x["fingerprint"])
        if len(pool) <= 100:
            sampled.extend(pool)
        else:
            rng = random.Random(args.seed)
            sampled.extend(rng.sample(pool, 100))

    with open(args.out_csv, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(
            ["rule", "confidence", "path", "startLine", "endLine", "testMethod",
             "message", "label", "reason", "labeler"]
        )
        for finding in sampled:
            writer.writerow(
                [
                    finding["rule"],
                    finding["confidence"],
                    finding["path"],
                    finding["startLine"],
                    finding["endLine"],
                    finding.get("testMethod", ""),
                    finding["message"],
                    "",
                    "",
                    "",
                ]
            )

    print(f"{len(sampled)} findings sampled from {len(findings)} total "
          f"(seed={args.seed}) -> {args.out_csv}")
    for rule, pool in sorted(by_rule.items()):
        print(f"  {rule}: {len(pool)} total, {min(len(pool), 100)} sampled")


if __name__ == "__main__":
    main()
