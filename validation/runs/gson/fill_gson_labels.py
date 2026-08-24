#!/usr/bin/env python3
"""One-off: apply the phase-1 gson labeling decisions (see run-log.md) to labels.csv."""
import csv

CSV = "C:/Users/Mert/Desktop/coverdict/validation/runs/gson/labels.csv"
LABELER = "claude-opus-5 (coverdict M1c-2 session, 2026-08-24)"

TRUE_POSITIVES = {
    ("CATCH_ORACLE_WITHOUT_FAIL", "gson/src/test/java/com/google/gson/metrics/PerformanceTest.java", "60"):
        "catch (JsonParseException expected) { break; } swallows the exception with no fail()/oracle anywhere in the method (also flagged NO_RECOGNIZED_ORACLE); real gap, independent of the Truth allowlist gap below.",
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/stream/JsonReaderTest.java", "2018"):
        "Method body only calls beginArray/skipValue/endArray; no assertion of any kind, in this method or a same-file helper. Genuinely oracle-less (implicit no-throw check only).",
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/metrics/PerformanceTest.java", "268"):
        "@Ignore'd performance-timing loop; only System.out.printf, no assertion anywhere in the method. Genuinely oracle-less.",
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/LinkedTreeMapTest.java", "193"):
        "Delegates to MoreAsserts.assertEqualsAndHashCode(...), a cross-class helper (different .java file). D-17: custom oracle APIs require explicit configuration; engine's same-compilation-unit helper traversal correctly does not reach across files. Real tool-scope limit (M1b 'custom oracle providers' open item), not a detector bug.",
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/stream/JsonReaderTest.java", "1643"):
        "Method body only calls beginArray/endArray on a BOM-prefixed reader; no assertion anywhere. Genuinely oracle-less.",
}

FALSE_POSITIVE_TRUTH_DIRECT = "Test method calls Truth's assertThat(...)/assertWithMessage(...) directly (file imports com.google.common.truth.Truth); Truth is not in the D-24 recognized-oracle allowlist (JUnit4/5, AssertJ, Mockito, Hamcrest), so a real oracle is present but unrecognized. Phase-1 canary calibration finding: add Truth to the allowlist."

FALSE_POSITIVE_TRUTH_HELPER = "Test method delegates to a private, same-compilation-unit helper method which itself calls Truth's assertThat(...); engine's helper traversal correctly reaches the call, but Truth is not in the D-24 allowlist so it is not recognized as an oracle. Same root cause as the direct-call false positives."

FALSE_POSITIVE_NOTES = {
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/functional/DefaultTypeAdaptersTest.java", "1004"): FALSE_POSITIVE_TRUTH_HELPER,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/functional/DefaultTypeAdaptersTest.java", "945"): FALSE_POSITIVE_TRUTH_HELPER,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/functional/CircularReferenceTest.java", "78"): FALSE_POSITIVE_TRUTH_HELPER,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/VersionExclusionStrategyTest.java", "70"): FALSE_POSITIVE_TRUTH_HELPER,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/bind/DefaultDateTypeAdapterTest.java", "45"):
        FALSE_POSITIVE_TRUTH_HELPER + " Note: this chain is two helper levels deep (assertFormattingAlwaysEmitsUsLocale -> assertFormatted -> assertThat); traversal depth beyond one level is unconfirmed as a separate variable here since Truth-recognition alone already explains the miss.",
    ("CATCH_ORACLE_WITHOUT_FAIL", "gson/src/test/java/com/google/gson/functional/ConcurrencyTest.java", "72"):
        "Worker-thread exceptions are captured via AtomicReference and asserted after the try (assertThat(error.get()).isNull()), plus an in-try Truth assertThat per iteration - exactly the pattern the rule spec's own 'Never fires on: oracle present after the try' exemption is designed to exempt. It still fires only because both assertThat calls are Truth (unrecognized), so the engine sees zero recognized oracles anywhere in the method. Same root cause as the NO_RECOGNIZED_ORACLE Truth gap, not a try/catch logic bug.",
}

with open(CSV, encoding="utf-8") as f:
    rows = list(csv.DictReader(f))
    fieldnames = list(rows[0].keys())

for row in rows:
    key = (row["rule"], row["path"], row["startLine"])
    if key in TRUE_POSITIVES:
        row["label"] = "true-positive"
        row["reason"] = TRUE_POSITIVES[key]
    elif key in FALSE_POSITIVE_NOTES:
        row["label"] = "false-positive"
        row["reason"] = FALSE_POSITIVE_NOTES[key]
    else:
        row["label"] = "false-positive"
        row["reason"] = FALSE_POSITIVE_TRUTH_DIRECT
    row["labeler"] = LABELER

with open(CSV, "w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(f, fieldnames=fieldnames)
    w.writeheader()
    w.writerows(rows)

from collections import Counter
print(Counter((r["rule"], r["label"]) for r in rows))
