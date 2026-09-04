#!/usr/bin/env python3
"""Contract spike (discardable, D-14): validate every golden example against
the verdict schema. Not production code; the production validator is Java.

Usage: python schema/validate-goldens.py   (from the repo root)
Exit 0 if all goldens validate, 1 otherwise.
"""
import json
import pathlib
import sys

from jsonschema import Draft202012Validator

here = pathlib.Path(__file__).parent
schema = json.loads((here / "proof-verdict.schema.json").read_text(encoding="utf-8"))
Draft202012Validator.check_schema(schema)
validator = Draft202012Validator(schema)

failed = False
for golden in sorted((here / "examples").glob("*.json")):
    doc = json.loads(golden.read_text(encoding="utf-8"))
    errors = sorted(validator.iter_errors(doc), key=lambda e: list(e.absolute_path))
    if errors:
        failed = True
        print(f"FAIL {golden.name}")
        for e in errors:
            loc = "/".join(str(p) for p in e.absolute_path) or "<root>"
            print(f"  at {loc}: {e.message}")
    else:
        print(f"PASS {golden.name}")

sys.exit(1 if failed else 0)
