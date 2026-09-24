# WAL-1423 conditional ITB diagnostic

This draft branch is **not mergeable**. Its sole remaining compatibility exception interprets a text-encoded mdoc device-key `kid` as bytes in memory while preserving the signed MSO and all other key checks. The exception has its own commit and `TODO [WAL-1423]` removal condition. The strict wallet and runner remain in draft PR #2255. The earlier issuer-metadata and JWE-`cty` exceptions have been removed after the issuer began enforcing key attestation and a fresh deployed payment request supplied protected `cty: JWT`.

Every JSON, Markdown and JUnit report from this branch is labelled diagnostic. A case still passes only with correlated wallet success and terminal ITB `SUCCESS`; the labels do not convert a conditional pass into a standards-compliant one. Hosted runs select the 15 unattended cases; the six payment cases require local operator authentication. Issuance must succeed before the remaining mdoc exception can be evaluated against fresh deployed credentials.

Compare this branch with the strict #2255 head using the same deployed case IDs and record both exact source revisions. Remove an exception when its corresponding upstream input is corrected, rerun the strict affected cases, and close this PR when the experiments are no longer needed.
