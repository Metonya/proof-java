#!/usr/bin/env python3
"""
tqa - test quality analyzer (prototype)

Four analyses, all driven by execution evidence rather than LLM judgement:

  1. overall coverage      - from the merged JaCoCo XML report
  2. new code coverage     - merged report intersected with `git diff` hunks
  3. uncovered lines       - per file, collapsed into ranges
  4. redundant tests       - per-test JaCoCo exec files, coverage-set subsumption
  5. weak oracles          - static scan of the test sources

Nothing here is Maven- or Gradle-specific: the inputs are a JaCoCo XML report,
a directory of per-test exec files, and a git working tree.
"""

import argparse
import fnmatch
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

# ---------------------------------------------------------------- coverage ---


def parse_report(xml_path):
    """Return {source_path: {line_no: (covered_instr, missed_instr)}} plus totals."""
    root = ET.parse(xml_path).getroot()
    files = {}
    for package in root.iter("package"):
        pkg = package.get("name")
        for sf in package.findall("sourcefile"):
            path = f"{pkg}/{sf.get('name')}"
            lines = {}
            for line in sf.findall("line"):
                nr = int(line.get("nr"))
                lines[nr] = (int(line.get("ci")), int(line.get("mi")),
                             int(line.get("cb", 0)), int(line.get("mb", 0)))
            files[path] = lines
    totals = {}
    for counter in root.findall("counter"):
        totals[counter.get("type")] = (
            int(counter.get("covered")),
            int(counter.get("missed")),
        )
    return files, totals


def line_status(ci, mi, *_):
    if ci == 0:
        return "MISSED"
    if mi > 0:
        return "PARTIAL"
    return "COVERED"


def to_ranges(numbers):
    """[3,4,5,9,10] -> ['3-5', '9-10']"""
    out, nums = [], sorted(numbers)
    i = 0
    while i < len(nums):
        j = i
        while j + 1 < len(nums) and nums[j + 1] == nums[j] + 1:
            j += 1
        out.append(str(nums[i]) if i == j else f"{nums[i]}-{nums[j]}")
        i = j + 1
    return out


def compute_metric(files, mode):
    """
    Three readings of the same JaCoCo data. They disagree on purpose - the point
    is to be explicit about which one you are quoting.

      jacoco-line : a line counts as covered if ANY instruction on it ran.
                    This is JaCoCo's own LINE counter, and what its HTML shows.
      strict      : a partially covered line does NOT count as covered.
                    Harsher, and closer to what a reviewer means by "tested".
      sonar       : SonarQube's blended metric, (CT + CF + LC) / (2B + EL).
                    In JaCoCo terms: (cb + LC) / (cb + mb + EL). This is the
                    only mode whose number should match the SonarQube UI.
    """
    cells = [cell for lines in files.values() for cell in lines.values()]
    executable = len(cells)
    lines_covered = sum(1 for ci, mi, cb, mb in cells if ci > 0)

    if mode == "jacoco-line":
        cov, total = lines_covered, executable
    elif mode == "strict":
        cov = sum(1 for ci, mi, cb, mb in cells if ci > 0 and mi == 0 and mb == 0)
        total = executable
    elif mode == "sonar":
        cb_total = sum(cb for ci, mi, cb, mb in cells)
        mb_total = sum(mb for ci, mi, cb, mb in cells)
        cov = cb_total + lines_covered
        total = cb_total + mb_total + executable
    else:
        raise SystemExit(f"unknown metric: {mode}")
    return {"covered": cov, "missed": total - cov,
            "percent": round(100 * cov / total, 1) if total else 0.0}


# ---------------------------------------------------------------- new code ---


def changed_lines(base_ref, repo):
    """{file_path: {line numbers added or modified vs base_ref}} from git diff."""
    cmd = ["git", "-C", str(repo), "diff", "--unified=0", base_ref, "--", "*.java"]
    diff = subprocess.run(cmd, capture_output=True, text=True, check=True).stdout
    result, current = defaultdict(set), None
    for raw in diff.splitlines():
        if raw.startswith("+++ b/"):
            current = raw[6:]
        elif raw.startswith("@@") and current:
            m = re.search(r"\+(\d+)(?:,(\d+))?", raw)
            if m:
                start = int(m.group(1))
                count = int(m.group(2) or 1)
                result[current].update(range(start, start + count))
    return result


def match_source(report_files, repo_path):
    """Map a git path like core/src/main/java/com/demo/X.java to the JaCoCo key com/demo/X.java."""
    for key in report_files:
        if repo_path.endswith("/" + key):
            return key
    return None


# ------------------------------------------------------------- redundancy ---


def per_test_lines(exec_dir, classfiles, sources, jacococli, workdir):
    """Run jacococli once per test exec file and return {test_id: frozenset((file, line))}."""
    tests_tsv = Path(exec_dir) / "tests.tsv"
    result = {}
    for row in tests_tsv.read_text().strip().splitlines():
        test_id, status, exec_name = row.split("\t")
        xml_out = Path(workdir) / (exec_name + ".xml")
        cmd = ["java", "-jar", jacococli, "report", str(Path(exec_dir) / exec_name),
               "--classfiles", classfiles, "--xml", str(xml_out)]
        for src in sources:
            cmd += ["--sourcefiles", src]
        subprocess.run(cmd, capture_output=True, check=True)
        files, _ = parse_report(xml_out)
        covered = {(f, nr) for f, lines in files.items()
                   for nr, cell in lines.items() if cell[0] > 0}
        result[test_id] = frozenset(covered)
    return result


def oracle_strength(test_id, oracles, weak_rules):
    """
    Rank how much a test actually pins down. Used to decide which member of a
    duplicate cluster to keep - never delete the one with the strongest oracle.
    """
    rules = weak_rules.get(test_id, set())
    if "NO_ORACLE" in rules:
        return 0
    if rules:
        return 1
    return 2 + len(oracles.get(test_id, ()))


def find_redundant(test_lines, oracles, weak_rules):
    """
    Two different relations, two different verdicts.

    IDENTICAL coverage -> a genuine duplicate cluster. Keep the member with the
    strongest oracle, propose deleting the rest.

    STRICT SUBSET (A c B) -> NOT a reason to delete A. A narrow, focused test is
    good design; the suspicious party is B, which may be an eager test bundling
    several behaviours. Flagging A here is the classic false positive that makes
    coverage-only redundancy detection untrustworthy.
    """
    findings = []

    clusters = defaultdict(list)
    for test_id, lines in test_lines.items():
        if lines:
            clusters[lines].append(test_id)

    for lines, members in clusters.items():
        if len(members) < 2:
            continue
        members.sort(key=lambda t: (-oracle_strength(t, oracles, weak_rules), t))
        keep = members[0]
        for drop in members[1:]:
            fp_drop, fp_keep = oracles.get(drop, frozenset()), oracles.get(keep, frozenset())
            if fp_drop <= fp_keep:
                confidence, why = "HIGH", "same lines executed and the same or weaker assertions"
            else:
                confidence, why = "MEDIUM", "same lines executed but the assertions differ - compare the oracles"
            findings.append({
                "kind": "DUPLICATE", "test": drop, "keep": keep,
                "confidence": confidence, "reason": why, "lines": len(lines),
            })

    # eager tests: one test whose coverage is exactly the union of >=2 focused tests
    representatives = {lines: sorted(m)[0] for lines, m in clusters.items()}
    for lines, big in representatives.items():
        contained = [t for l, t in representatives.items() if l < lines and l]
        if len(contained) < 2:
            continue
        union = frozenset().union(*(test_lines[t] for t in contained))
        if union == lines:
            findings.append({
                "kind": "EAGER_TEST", "test": big, "keep": None,
                "confidence": "MEDIUM",
                "reason": "covers exactly what " + ", ".join(sorted(contained)[:3])
                          + " already cover together - split it or drop it, but keep the focused tests",
                "lines": len(lines),
            })
    return findings


# ----------------------------------------------------------- weak oracles ---

ASSERT_RE = re.compile(r"\b(assert\w*|verify|expect|should\w*)\s*\(")
TEST_RE = re.compile(r"@Test\b")
TAUTOLOGY_RE = re.compile(
    r"assert(True|False)\s*\(\s*[\w.()]+\s*(?:[!<>=]=|[<>])\s*(?:0|null|-1)\s*\)"
)


def split_test_methods(source):
    """Yield (method_name, body, start_line) for each @Test method. Brace matching, no parser."""
    for match in TEST_RE.finditer(source):
        sig = source.find("(", match.end())
        name_zone = source[match.end():sig]
        name = name_zone.strip().split()[-1] if name_zone.strip() else "?"
        brace = source.find("{", sig)
        if brace < 0:
            continue
        depth, i = 0, brace
        while i < len(source):
            if source[i] == "{":
                depth += 1
            elif source[i] == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        yield name, source[brace:i + 1], source[:match.start()].count("\n") + 1


def scan_oracles(test_roots, exclude_globs):
    """Return (findings, {test_id: fingerprints}, {test_id: rule names})."""
    findings, fingerprints, rules_by_test = [], {}, defaultdict(set)
    for root in test_roots:
        for path in sorted(Path(root).rglob("*.java")):
            rel = str(path)
            if any(fnmatch.fnmatch(rel, g) for g in exclude_globs):
                continue
            source = path.read_text()
            pkg = re.search(r"package\s+([\w.]+)\s*;", source)
            cls = re.search(r"(?:class|record)\s+(\w+)", source)
            if not cls:
                continue
            fqcn = f"{pkg.group(1)}.{cls.group(1)}" if pkg else cls.group(1)

            for name, body, line_no in split_test_methods(source):
                test_id = f"{fqcn}#{name}"
                asserts = ASSERT_RE.findall(body)
                fingerprints[test_id] = frozenset(
                    re.sub(r"\s+", "", s) for s in
                    re.findall(r"\b(?:assert\w*|verify)\s*\([^;]*\)", body)
                )
                if not asserts:
                    findings.append({
                        "test": test_id, "line": line_no, "rule": "NO_ORACLE",
                        "severity": "HIGH",
                        "message": "test executes production code but asserts nothing",
                    })
                    rules_by_test[test_id].add("NO_ORACLE")
                    continue
                if TAUTOLOGY_RE.search(body):
                    findings.append({
                        "test": test_id, "line": line_no, "rule": "TAUTOLOGICAL_ORACLE",
                        "severity": "MEDIUM",
                        "message": "assertion holds for almost any implementation",
                    })
                if re.search(r"catch\s*\([^)]*\)\s*\{[^}]*assert", body):
                    findings.append({
                        "test": test_id, "line": line_no, "rule": "ORACLE_IN_CATCH",
                        "severity": "MEDIUM",
                        "message": "the only assertion sits in a catch block that normally never runs",
                    })
                if all(a.startswith("assertNotNull") for a in asserts):
                    findings.append({
                        "test": test_id, "line": line_no, "rule": "NULL_CHECK_ONLY",
                        "severity": "MEDIUM",
                        "message": "assertNotNull is the only oracle",
                    })
    for f in findings:
        rules_by_test[f["test"]].add(f["rule"])
    return findings, fingerprints, rules_by_test



# ------------------------------------------------------------------- html ---

HTML_CSS = """
body{font:14px/1.6 system-ui,sans-serif;margin:0;background:#f6f6f4;color:#23231f}
.wrap{max-width:1000px;margin:0 auto;padding:32px 20px}
h1{font-size:22px;font-weight:500;margin:0 0 4px}
h2{font-size:16px;font-weight:500;margin:32px 0 10px;color:#44443f}
.sub{color:#6b6b64;margin:0 0 24px}
.cards{display:flex;gap:12px;flex-wrap:wrap}
.card{flex:1;min-width:180px;background:#fff;border:1px solid #e2e2dc;border-radius:12px;padding:16px}
.card .n{font-size:26px;font-weight:500}
.card .l{color:#6b6b64;font-size:13px}
table{width:100%;border-collapse:collapse;background:#fff;border:1px solid #e2e2dc;border-radius:12px;overflow:hidden}
th{text-align:left;font-weight:500;font-size:13px;color:#6b6b64;padding:10px 14px;border-bottom:1px solid #e2e2dc}
td{padding:10px 14px;border-bottom:1px solid #f0f0ec;vertical-align:top}
tr:last-child td{border-bottom:none}
code{font-family:ui-monospace,monospace;font-size:12.5px;background:#f0f0ec;padding:1px 5px;border-radius:4px}
.tag{display:inline-block;font-size:11.5px;padding:2px 8px;border-radius:20px;font-weight:500}
.HIGH{background:#fceaea;color:#a32d2d}.MEDIUM{background:#faeeda;color:#854f0b}.LOW{background:#f1efe8;color:#5f5e5a}
.bar{height:6px;background:#e8e8e2;border-radius:3px;margin-top:8px;overflow:hidden}
.bar>i{display:block;height:100%;background:#1d9e75}
.bar.low>i{background:#e24b4a}
.empty{color:#6b6b64;background:#fff;border:1px solid #e2e2dc;border-radius:12px;padding:16px}
"""


def esc(text):
    return (str(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


def write_html(result, path, base):
    o, n = result["overall"], result["new_code"]
    rows = []

    def card(value, label, pct=None):
        bar = ""
        if pct is not None:
            cls = "bar low" if pct < 80 else "bar"
            bar = f'<div class="{cls}"><i style="width:{min(pct,100)}%"></i></div>'
        return f'<div class="card"><div class="n">{value}</div><div class="l">{esc(label)}</div>{bar}</div>'

    new_txt = "n/a" if n["percent"] is None else f'{n["percent"]}%'
    cards = "".join([
        card(f'{o["percent"]}%', f'overall ({o["metric"]})', o["percent"]),
        card(new_txt, f'new code vs {base}', n["percent"]),
        card(len(result["weak_oracles"]), "weak oracles"),
        card(len(result["redundant_tests"]), "redundant candidates"),
    ])

    def table(headers, body_rows, empty_msg):
        if not body_rows:
            return f'<div class="empty">{esc(empty_msg)}</div>'
        head = "".join(f"<th>{esc(h)}</th>" for h in headers)
        return f"<table><tr>{head}</tr>{''.join(body_rows)}</table>"

    new_rows = [f'<tr><td><code>{esc(f)}</code></td><td><code>{esc(", ".join(r))}</code></td></tr>'
                for f, r in n["uncovered"].items()]
    unc_rows = [f'<tr><td><code>{esc(f)}</code></td><td><code>{esc(", ".join(r))}</code></td></tr>'
                for f, r in sorted(result["uncovered"].items())]
    weak_rows = [
        f'<tr><td><span class="tag {w["severity"]}">{esc(w["rule"])}</span></td>'
        f'<td><code>{esc(w["test"])}</code><br><span class="l">{esc(w["message"])}</span></td></tr>'
        for w in result["weak_oracles"]]
    red_rows = []
    for r in result["redundant_tests"]:
        action = f'delete, keep <code>{esc(r["keep"])}</code>' if r["keep"] else "review / split"
        red_rows.append(
            f'<tr><td><span class="tag {r["confidence"]}">{esc(r["kind"])}</span></td>'
            f'<td><code>{esc(r["test"])}</code><br><span class="l">{action} &mdash; {esc(r["reason"])}</span></td></tr>')

    html = f"""<!doctype html><meta charset=utf-8><title>Test quality report</title>
<style>{HTML_CSS}</style><div class=wrap>
<h1>Test quality report</h1>
<p class=sub>new code measured against <code>{esc(base)}</code></p>
<div class=cards>{cards}</div>
<h2>Uncovered lines in new code</h2>
{table(["file", "lines"], new_rows, "Every changed executable line is covered.")}
<h2>Weak oracles</h2>
{table(["rule", "test"], weak_rows, "No weak oracles found.")}
<h2>Redundant test candidates</h2>
{table(["kind", "test"], red_rows, "No redundant tests found.")}
<h2>All uncovered lines</h2>
{table(["file", "lines"], unc_rows, "Everything is covered.")}
</div>"""
    Path(path).write_text(html)

# ------------------------------------------------------------------- main ---


def main():
    ap = argparse.ArgumentParser(prog="tqa")
    ap.add_argument("--report", required=True, help="merged JaCoCo XML report")
    ap.add_argument("--repo", default=".")
    ap.add_argument("--base", default="HEAD", help="new code reference (branch, tag or commit)")
    ap.add_argument("--exec-dir", help="directory of per-test .exec files + tests.tsv")
    ap.add_argument("--classfiles")
    ap.add_argument("--sources", nargs="*", default=[])
    ap.add_argument("--test-sources", nargs="*", default=[])
    ap.add_argument("--jacococli")
    ap.add_argument("--metric", default="jacoco-line",
                    choices=["jacoco-line", "strict", "sonar"],
                    help="which coverage definition to quote")
    ap.add_argument("--exclude", nargs="*", default=[], help="glob patterns to skip")
    ap.add_argument("--workdir", default="/tmp/tqa")
    ap.add_argument("--json", help="write the full result as JSON")
    ap.add_argument("--html", help="write a standalone HTML report")
    args = ap.parse_args()
    Path(args.workdir).mkdir(parents=True, exist_ok=True)

    files, totals = parse_report(args.report)
    files = {k: v for k, v in files.items()
             if not any(fnmatch.fnmatch(k, g) for g in args.exclude)}

    # Recompute from the FILTERED files rather than the report's root counter.
    # If exclusions were applied to the listings but not to the totals, the
    # headline percentage would silently disagree with the detail below it -
    # and with whatever the CI server reports.
    overall = compute_metric(files, args.metric)
    overall["excluded_globs"] = args.exclude
    overall["metric"] = args.metric

    # new code
    diff = changed_lines(args.base, args.repo)
    new_cov = new_missed = 0
    new_uncovered = defaultdict(list)
    for git_path, lines in diff.items():
        key = match_source(files, git_path)
        if not key:
            continue
        for nr in lines:
            if nr not in files[key]:
                continue  # not an executable line
            if line_status(*files[key][nr]) == "COVERED":
                new_cov += 1
            else:
                new_missed += 1
                new_uncovered[git_path].append(nr)
    new_code = {
        "covered": new_cov, "missed": new_missed,
        "percent": round(100 * new_cov / (new_cov + new_missed), 1)
        if new_cov + new_missed else None,
        "uncovered": {f: to_ranges(v) for f, v in new_uncovered.items()},
    }

    # uncovered lines everywhere
    uncovered = {}
    for key, lines in files.items():
        misses = [nr for nr, cell in lines.items() if line_status(*cell) != "COVERED"]
        if misses:
            uncovered[key] = to_ranges(misses)

    oracle_findings, fingerprints, weak_rules = scan_oracles(args.test_sources, args.exclude)

    redundant = []
    if args.exec_dir and args.classfiles and args.jacococli:
        tl = per_test_lines(args.exec_dir, args.classfiles, args.sources,
                            args.jacococli, args.workdir)
        redundant = find_redundant(tl, fingerprints, weak_rules)

    result = {"overall": overall, "new_code": new_code, "uncovered": uncovered,
              "weak_oracles": oracle_findings, "redundant_tests": redundant}
    if args.json:
        Path(args.json).write_text(json.dumps(result, indent=2))
    if args.html:
        write_html(result, args.html, args.base)
    render(result, args.base)
    return 0


def render(r, base):
    p = print
    p("=" * 72)
    p("  TEST QUALITY REPORT")
    p("=" * 72)
    o = r["overall"]
    p(f"\nOverall coverage      : {o['percent']}%  ({o['covered']}/{o['covered']+o['missed']})"
      f"   [metric: {o['metric']}]")
    n = r["new_code"]
    if n["percent"] is None:
        p(f"New code coverage     : no changed executable lines vs {base}")
    else:
        p(f"New code coverage     : {n['percent']}%  ({n['covered']}/{n['covered']+n['missed']})"
          f"   [vs {base}]")
        for f, ranges in n["uncovered"].items():
            p(f"    uncovered in new code -> {f}: {', '.join(ranges)}")

    p(f"\n-- uncovered lines ({len(r['uncovered'])} files) " + "-" * 30)
    for f, ranges in sorted(r["uncovered"].items()):
        p(f"  {f}: {', '.join(ranges)}")

    p(f"\n-- weak oracles ({len(r['weak_oracles'])}) " + "-" * 38)
    for f in r["weak_oracles"]:
        p(f"  [{f['severity']:6}] {f['rule']:22} {f['test']}")
        p(f"           {f['message']}")

    p(f"\n-- redundant test candidates ({len(r['redundant_tests'])}) " + "-" * 22)
    for f in r["redundant_tests"]:
        p(f"  [{f['confidence']:6}] {f['kind']:10} {f['test']}")
        if f["keep"]:
            p(f"           safe to delete - keep {f['keep']}")
        p(f"           {f['reason']}")
    p("")


if __name__ == "__main__":
    sys.exit(main())
