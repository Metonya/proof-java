# Release contract (M0 deliverable 7)

What every coverdict release ships and promises. Applies from v0.1.

## License and notices

- Project license: **Apache-2.0** (`LICENSE` at repo root, canonical text).
- `NOTICE` and a full transitive dependency/license inventory are **generated
  at release build time** (e.g. license-maven-plugin), never hand-maintained;
  a release with a missing or stale inventory does not ship.
- Inventory gate: no LGPL at any scope (hard rule 9 / D-20); JaCoCo's EPL-2.0
  obligations (notice + source availability for the redistributed agent/lib)
  are satisfied in the release artifacts.
- Trademark posture per hard rule 9: third-party marks are adjectival in docs
  only, never CLI values, package, or artifact names.

## Distribution channel

- **GitHub Releases** is the only channel for v0.x: one executable jar (D-01)
  per release plus `NOTICE`, dependency inventory, and checksums.
- Maven Central publication is deferred until the M3 build plugin exists and
  needs it; adding a channel is a DECISIONS entry.

## Integrity

- Every artifact ships a `SHA-256SUMS` file; the release notes state the jar's
  SHA-256. Signing (Sigstore or GPG) is evaluated at M3 alongside CI; until
  then checksums are the integrity mechanism.

## Versioning and support window

- SemVer with 0.x semantics: the JSON schema is the product surface (hard
  rule 7); during 0.x a schema-breaking change increments the minor version
  and requires a DECISIONS entry (D-01).
- Support window during 0.x: **latest release only** — fixes land in the next
  release, no backports. Revisit at v1.
- Each release pins and records: JDK used to build, dependency versions, and
  the schema version it emits.
