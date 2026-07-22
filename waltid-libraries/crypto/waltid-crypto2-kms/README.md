# waltid-crypto2-kms

Multiplatform REST key providers for `waltid-crypto2`.

Provider configuration stores only credential references. Applications resolve those references to credentials at operation time and remain responsible for the injected Ktor `HttpClient` lifecycle.

Implemented providers:

- HashiCorp Vault Transit with token, AppRole, and userpass authentication
- Azure Key Vault with OAuth client credentials
- AWS KMS with SigV4 access-key and temporary-session credentials
- OCI KMS with API-key HTTP Signature authentication

Optional JVM extensions:

- `waltid-crypto2-kms-aws-sdk` for AWS default credentials, multi-region replicas, aliases, tags, and regional failover
- `waltid-crypto2-kms-azure-identity` for Azure managed, workload, and default credentials

OCI SDK principal authentication remains in the legacy adapter until there is a concrete crypto2 consumer; the portable REST provider should be preferred otherwise.
