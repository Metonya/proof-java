#!/usr/bin/env python3
"""Round 2: apply the calibration-round-2 labeling decisions to labels.csv."""
import csv
from pathlib import Path

CSV = str(Path(__file__).with_name("labels.csv"))
LABELER = "claude-opus-5 (coverdict M1c-2 round 2 session, 2026-08-24)"

CROSS_CLASS_REASON = ("Delegates to a public static helper defined in a DIFFERENT .java file "
    "(e.g. MoreAsserts, or DefaultTypeAdaptersTest.assertEqualsDate/Time called from "
    "SqlTypesGsonTest) which itself contains a real oracle. D-17: custom/external oracle "
    "helpers require explicit configuration; the engine's helper traversal is documented "
    "to stay inside the same compilation unit. Tool-scope limit, not a detector bug - same "
    "category as round 1's LinkedTreeMapTest/MoreAsserts case.")

EMPTY_REASON = "No assertion of any kind, in the method or any traversed helper. Genuinely oracle-less."
IGNORED_PERF_REASON = "Ignore-annotated exploratory/performance test; only System.out printing, no assertion anywhere. Genuinely oracle-less."
NO_THROW_REASON = "Only exercises the API and relies on the absence of a thrown exception (own comment says so, or an unused result variable); no assertion on any observed value. Genuinely weak/no oracle."

TRUE_POSITIVES = {
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/metrics/PerformanceTest.java", "169"): IGNORED_PERF_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/JsonArrayTest.java", "34"): CROSS_CLASS_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/JsonPrimitiveTest.java", "307"): CROSS_CLASS_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/sql/SqlTypesGsonTest.java", "105"): CROSS_CLASS_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/bind/JsonElementReaderTest.java", "167"): EMPTY_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/JsonPrimitiveTest.java", "290"): CROSS_CLASS_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/functional/EnumTest.java", "84"): CROSS_CLASS_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/JsonNullTest.java", "31"): CROSS_CLASS_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/bind/JsonElementReaderTest.java", "206"): EMPTY_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/bind/JsonTreeReaderTest.java", "282"): CROSS_CLASS_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/metrics/PerformanceTest.java", "200"): IGNORED_PERF_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/metrics/PerformanceTest.java", "282"): "Ignore-annotated; result assigned to a variable literally named unused, never checked. Genuinely oracle-less.",
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/bind/JsonTreeWriterTest.java", "281"): CROSS_CLASS_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/metrics/PerformanceTest.java", "154"): IGNORED_PERF_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/LinkedTreeMapTest.java", "193"): CROSS_CLASS_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/stream/JsonReaderTest.java", "2018"): EMPTY_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/GsonBuilderTest.java", "358"): NO_THROW_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/sql/SqlTypesGsonTest.java", "122"): CROSS_CLASS_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/metrics/PerformanceTest.java", "268"): IGNORED_PERF_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/GsonBuilderTest.java", "397"): NO_THROW_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/metrics/PerformanceTest.java", "217"): IGNORED_PERF_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/metrics/PerformanceTest.java", "250"): IGNORED_PERF_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/bind/JsonElementReaderTest.java", "111"): EMPTY_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/functional/TypeHierarchyAdapterTest.java", "139"): "GsonBuilder chain assigned to unused; no assertion. Genuinely weak/no oracle.",
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/JsonObjectTest.java", "156"): CROSS_CLASS_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/metrics/PerformanceTest.java", "231"): IGNORED_PERF_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/sql/SqlTypesGsonTest.java", "54"): CROSS_CLASS_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/stream/JsonReaderTest.java", "1643"): EMPTY_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/sql/SqlTypesGsonTest.java", "72"): CROSS_CLASS_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/GsonBuilderTest.java", "178"): "Loop registering type adapters; no assertion. Genuinely weak/no oracle.",
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/stream/JsonReaderTest.java", "2001"): EMPTY_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/bind/JsonElementReaderTest.java", "175"): EMPTY_REASON,
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/metrics/PerformanceTest.java", "118"): IGNORED_PERF_REASON,
}

FALSE_POSITIVES = {
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/functional/DefaultTypeAdaptersTest.java", "217"):
        "Calls the private 1-arg testNullSerializationAndDeserialization(Class) 34 times; traversal "
        "correctly follows it (same file, private), but that method's only statement calls the "
        "public static 2-arg overload (same file) - traversal stops there (OracleRecognizer only "
        "descends into private declarations). The 2-arg overload contains two real Truth assertThat "
        "calls. Real oracle exists; a distinct gap from the Truth-recognition fix - the same-file "
        "private-only traversal boundary, not tested until now.",
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/internal/sql/SqlTypesGsonTest.java", "31"):
        "Same root cause as DefaultTypeAdaptersTest testNullSerialization: this file's own private "
        "1-arg helper calls DefaultTypeAdaptersTest's public static 2-arg overload (cross-file too), "
        "which contains the real Truth assertions. Real oracle exists, not reachable by traversal.",
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/functional/DefaultTypeAdaptersTest.java", "444"):
        "Calls assertEqualsDate/assertEqualsTime directly and unqualified - both are public static "
        "methods defined later in this same file, each containing real Truth assertThat calls. Same "
        "private-only traversal boundary as testNullSerialization above, hit directly this time "
        "instead of through an intermediate private wrapper.",
}

UNCLEAR = {
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/functional/CircularReferenceTest.java", "50"):
        "assertThrowsStackOverflow(() -> ...) fails to resolve (JavaParser symbol-solver limitation "
        "with overloaded methods taking a functional-interface/lambda argument - unrelated to Truth). "
        "Ground truth: the helper contains a real oracle (assertThrows plus a Truth assertThat on the "
        "root cause). Tool correctly reports INCONCLUSIVE rather than a false HIGH claim (D-17) - the "
        "right behavior given genuine uncertainty, but the underlying resolution gap is real and "
        "separate from this session's fix.",
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/functional/CircularReferenceTest.java", "69"):
        "Same as testCircularSerialization above - identical unresolved-lambda-argument cause.",
    ("NO_RECOGNIZED_ORACLE", "gson/src/test/java/com/google/gson/functional/CircularReferenceTest.java", "78"):
        "Same as testCircularSerialization above - identical unresolved-lambda-argument cause.",
}

with open(CSV, encoding="utf-8") as f:
    rows = list(csv.DictReader(f))
    fieldnames = list(rows[0].keys())

for row in rows:
    key = (row["rule"], row["path"], row["startLine"])
    if key in TRUE_POSITIVES:
        row["label"] = "true-positive"
        row["reason"] = TRUE_POSITIVES[key]
    elif key in FALSE_POSITIVES:
        row["label"] = "false-positive"
        row["reason"] = FALSE_POSITIVES[key]
    elif key in UNCLEAR:
        row["label"] = "unclear"
        row["reason"] = UNCLEAR[key]
    else:
        raise SystemExit("unlabeled row: " + str(key))
    row["labeler"] = LABELER

with open(CSV, "w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(f, fieldnames=fieldnames)
    w.writeheader()
    w.writerows(rows)

from collections import Counter
print(Counter((r["confidence"], r["label"]) for r in rows))
