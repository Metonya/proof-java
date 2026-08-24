#!/usr/bin/env python3
"""Classify the assertj labels.csv sample by likely ground-truth category,
for the agent to spot-check before final labeling (D-34)."""
import csv
import os
import re
import sys
from collections import Counter

labels_csv, repo_root = sys.argv[1], sys.argv[2]

with open(labels_csv, encoding="utf-8") as f:
    rows = list(csv.DictReader(f))

ASSERT_THAT_XXX = re.compile(r"\bassertThat\w*\s*\(")
THEN_XXX = re.compile(r"\bthen\w*\s*\(")
SOFT_RECEIVER = re.compile(r"\b\w*(?:softly|soft|Softly)\w*\s*\.\s*(?:assertThat|then)\w*\s*\(")
VERIFY_CALL = re.compile(r"\bverify\w*\s*\(")

file_head_cache = {}

def head(path):
    if path not in file_head_cache:
        with open(os.path.join(repo_root, path), encoding="utf-8") as f:
            file_head_cache[path] = f.read(6000)
    return file_head_cache[path]

counts = Counter()
for row in rows:
    path, start, end = row["path"], int(row["startLine"]), int(row["endLine"])
    with open(os.path.join(repo_root, path), encoding="utf-8") as f:
        lines = f.readlines()
    body = "".join(lines[start - 1:end])
    h = head(path)

    category = None
    if SOFT_RECEIVER.search(body):
        category = "soft_assertions_instance"
    elif "expectAssertionError" in body or "AssertionsUtil" in body:
        category = "crossfile_helper"
    elif ASSERT_THAT_XXX.search(body) or THEN_XXX.search(body):
        category = "assertj_call_present_but_flagged"
    elif VERIFY_CALL.search(body):
        category = "verify_call_present"
    elif re.search(r"\.\w+\([^)]*\)\s*;\s*$", body.strip(), re.M) and "assertThat" not in body:
        category = "bare_method_call_no_assertion"
    else:
        category = "other_needs_read"

    counts[category] += 1
    row["_category"] = category

with open(labels_csv.replace(".csv", "-augmented.csv"), "w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
    w.writeheader()
    w.writerows(rows)

for k, v in counts.most_common():
    print(f"{k:38s} {v:4d}")
