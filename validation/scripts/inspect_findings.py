#!/usr/bin/env python3
"""Dump the source body for each row in labels.csv, for agent review.
Usage: python inspect_findings.py <labels.csv> <repo-root>
"""
import csv
import sys

labels_csv, repo_root = sys.argv[1], sys.argv[2]

with open(labels_csv, encoding="utf-8") as f:
    rows = list(csv.DictReader(f))

for i, row in enumerate(rows):
    path = f"{repo_root}/{row['path']}"
    start, end = int(row["startLine"]), int(row["endLine"])
    with open(path, encoding="utf-8") as f:
        lines = f.readlines()
    body = "".join(lines[start - 1:end])
    print(f"=== [{i}] {row['rule']} {row['path']}:{start}-{end} ===")
    print(body)
    print()
