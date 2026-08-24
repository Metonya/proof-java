#!/usr/bin/env python3
import csv, sys, re

labels_csv, repo_root = sys.argv[1], sys.argv[2]

with open(labels_csv, encoding="utf-8") as f:
    rows = list(csv.DictReader(f))

file_imports_cache = {}

def imports_for(path):
    if path not in file_imports_cache:
        with open(f"{repo_root}/{path}", encoding="utf-8") as f:
            text = f.read(4000)
        file_imports_cache[path] = re.findall(r"^import (?:static )?([\w.]+);", text, re.M)
    return file_imports_cache[path]

TRUTH_MARKERS = ("com.google.common.truth",)
counts = {}
for row in rows:
    path = row["path"]
    start, end = int(row["startLine"]), int(row["endLine"])
    with open(f"{repo_root}/{path}", encoding="utf-8") as f:
        lines = f.readlines()
    body = "".join(lines[start - 1:end])
    imps = imports_for(path)
    uses_truth_import = any(m in i for i in imps for m in TRUTH_MARKERS)
    calls_assertThat = "assertThat(" in body
    calls_assertWithMessage = "assertWithMessage(" in body
    calls_fail = re.search(r"\bfail\(", body) is not None
    calls_any_assert = re.search(r"\bassert\w*\(", body) is not None
    calls_verify = re.search(r"\bverify\(", body) is not None
    key = (uses_truth_import, calls_assertThat or calls_assertWithMessage, calls_any_assert, calls_fail, calls_verify)
    counts[key] = counts.get(key, 0) + 1
    row["_uses_truth_import"] = uses_truth_import
    row["_calls_assertThat_or_WithMessage"] = calls_assertThat or calls_assertWithMessage
    row["_calls_any_assert"] = calls_any_assert
    row["_calls_fail"] = calls_fail
    row["_calls_verify"] = calls_verify

print("pattern counts (truth_import, assertThat/WithMessage_call, any_assert_call, fail_call, verify_call) -> n")
for k, v in sorted(counts.items(), key=lambda kv: -kv[1]):
    print(k, "->", v)

# write augmented csv for manual spot check
with open(labels_csv.replace(".csv", "-augmented.csv"), "w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
    w.writeheader()
    w.writerows(rows)
