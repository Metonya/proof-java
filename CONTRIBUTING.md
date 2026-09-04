# Contributing

## Building

Requires JDK 17 and Maven 3.9+. JDK 17 specifically — the embedded mutation
engine cannot read class files from a newer JDK, so L2/L3 will refuse to run
above Java 22 (see the support table in the README).

```bash
mvn verify                                    # 400+ tests
mvn -Pmutation-it -pl proof-java-cli verify   # slow; drives PIT in a subprocess
python schema/validate-goldens.py             # goldens satisfy the schema
sha256sum -c validation/SHA256SUMS            # pinned fixtures unchanged
```

`mvn -Pmutation-it` is the only automated check that the chain between
proof-java and PIT — the `META-INF/services` registrations, the PIT feature id,
the system properties passed across the process boundary, the progress marker —
is still intact. It is worth running before any change that touches
`analysis/mutation`, `analysis/pertest` or `analysis/subprocess`, because every
link in that chain fails silently rather than loudly.

## Before opening a pull request

- Read [`AGENTS.md`](AGENTS.md). It applies to humans too; the hard rules are
  the design constraints this project is built on, and a change that breaks one
  needs to say so explicitly.
- A new decision, a rejected alternative, or a reversal gets an entry in
  [`docs/DECISIONS.md`](docs/DECISIONS.md) — a few lines, dated. Don't
  relitigate a settled decision silently; propose a new entry.
- Any numeric claim in a doc needs the command that reproduces it.
- New rules need a spec in [`docs/rules/`](docs/rules/) and fixtures under
  `fixtures/rules/<RULE>/` covering the positive, negative and unresolved cases.

## The rule that matters most

**Unknown is never green.** If evidence is missing, stale, ambiguous or
unusable, the run reports it as an incomplete result with a named reason. It is
never dropped from a denominator, never rounded away, and never turned into a
clean-looking number. A change that makes output look better by hiding what
could not be established will be rejected, however small.

## Reporting a bug

Include the exact command, the JDK version (`java -version`), and the verdict
JSON if one was written. `proof-java doctor` output helps for anything about a
repository not being analysable.

For a suspected wrong finding, the most useful report names the test and says
why you consider it correct — that is precisely the labelled data this project
measures precision against.

## Security

Untrusted input is a real part of this tool's threat model: it parses JaCoCo
XML, git output and Java sources from repositories that may be hostile. See
[`docs/SECURITY-POLICY.md`](docs/SECURITY-POLICY.md). Report a vulnerability
privately through GitHub's security advisory form rather than a public issue.

## License

Contributions are accepted under Apache-2.0, the project's license.
