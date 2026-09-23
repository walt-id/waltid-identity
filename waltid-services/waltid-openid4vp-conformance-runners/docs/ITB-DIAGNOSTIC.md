# WAL-1423 conditional ITB diagnostic

This draft branch is **not mergeable**. It tests three specific malformed or contradictory inputs from the deployed ITB reference services: empty advertised key-attestation requirements, text-encoded mdoc device-key `kid`, and missing outer JWE `cty`. Each exception has its own commit and `TODO [WAL-1423]` removal condition. The strict wallet and runner remain in draft PR #2255.

Every JSON, Markdown and JUnit report from this branch is labelled diagnostic. A case still passes only with correlated wallet success and terminal ITB `SUCCESS`; the labels do not convert a conditional pass into a standards-compliant one. Ordinary-payment verifier selection and portal timeouts remain visible failures, not wallet-side bypasses.

Compare this branch with the strict #2255 head using the same deployed case IDs and record both exact source revisions. Remove an exception when its corresponding upstream input is corrected, rerun the strict affected cases, and close this PR when the experiments are no longer needed.
