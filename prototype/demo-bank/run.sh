#!/usr/bin/env bash
# End-to-end demo: compile, run tests with per-test coverage, analyse.
#
#   ./run.sh                      run everything
#   ./run.sh --test DiscountCalculatorTest#calculatesGoldDiscount
#                                 run a single test method
#   ./run.sh --base master        measure new code against another ref
#
set -euo pipefail
cd "$(dirname "$0")"

JACOCO="${JACOCO_HOME:-../tools/jacoco}"
AGENT="$JACOCO/lib/jacocoagent.jar"
CLI="$JACOCO/lib/jacococli.jar"
METRIC=sonar
BASE=master
ONLY=""

while [ $# -gt 0 ]; do
  case "$1" in
    --test)   ONLY="$2"; shift 2 ;;
    --base)   BASE="$2"; shift 2 ;;
    --metric) METRIC="$2"; shift 2 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

[ -f "$AGENT" ] || { echo "JaCoCo not found at $JACOCO - set JACOCO_HOME" >&2; exit 1; }

ALL_TESTS="com.demo.core.DiscountCalculatorTest com.demo.core.IbanValidatorTest \
com.demo.core.AccountDtoTest com.demo.api.FeeServiceTest"
TESTS="${ONLY:-$ALL_TESTS}"
if [ -n "$ONLY" ] && [[ "$ONLY" != com.demo.* ]]; then
  # allow the short form: DiscountCalculatorTest#foo
  for t in $ALL_TESTS; do
    case "$t" in *".${ONLY%%#*}") TESTS="$t${ONLY#*"${ONLY%%#*}"}" ;; esac
  done
fi

echo "==> compiling"
rm -rf build && mkdir -p build/classes build/test-classes build/exec
javac -d build/classes -cp "$AGENT" \
  harness/src/main/java/com/tinytest/*.java \
  core/src/main/java/com/demo/core/*.java \
  api/src/main/java/com/demo/api/*.java
javac -d build/test-classes -cp build/classes \
  core/src/test/java/com/demo/core/*.java \
  api/src/test/java/com/demo/api/*.java

echo "==> running tests (one JaCoCo exec dump per test)"
java -javaagent:"$AGENT"=output=none,includes=com.demo.* \
  -cp "build/classes:build/test-classes" \
  com.tinytest.Runner build/exec $TESTS

echo "==> merging coverage"
java -jar "$CLI" merge build/exec/*.exec --destfile build/merged.exec >/dev/null
java -jar "$CLI" report build/merged.exec \
  --classfiles build/classes/com/demo \
  --sourcefiles core/src/main/java --sourcefiles api/src/main/java \
  --xml build/jacoco.xml --html build/jacoco-html >/dev/null

echo "==> analysing"
python3 ../tqa/tqa.py \
  --report build/jacoco.xml --repo . --base "$BASE" --metric "$METRIC" \
  --exec-dir build/exec --classfiles build/classes/com/demo \
  --sources core/src/main/java api/src/main/java \
  --test-sources core/src/test/java api/src/test/java \
  --jacococli "$CLI" \
  --exclude '*Dto.java' \
  --json build/tqa.json --html build/tqa.html

echo
echo "reports: build/tqa.html (findings)  build/jacoco-html/index.html (coverage)"
