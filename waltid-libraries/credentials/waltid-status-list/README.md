# Status lists

`TokenStatusListCodec` encodes and decodes IETF Token Status Lists. The common
`IetfStatusListToken.fromJwt(header, payload, expectedUri)` API checks decoded JWT
structure, required claims, the exact subject URI, supported bit widths and unpadded
base64url encoding. Decompression is bounded to 16 MiB by default; consumers can set a
smaller `maximumOutputBytes`. `readStatus(index)` rejects negative and out-of-range indices.

This API does not verify a signature or authorize a signer. The caller must verify the
original JWT with its cryptographic library and trusted signer context before using the
claims or status. It does not discover keys from JWT headers, perform HTTP requests, or
make an issuance or verification policy decision.

JWT `typ` accepts `statuslist+jwt` and `application/statuslist+jwt`, case-insensitively
according to JOSE media-type rules. Required `iat` and optional `exp`, `nbf` and `ttl`
must be finite JSON numbers, preserving fractional seconds. Numeric strings and explicit
nulls are rejected; TTL must be positive and expiry must follow issuance.

Call `token.validity.isValidAt(current, resolvedAt, maximumAgeSeconds, clockSkewSeconds)`
whenever a list is used. This checks issuance, signed expiry, validity start, retrieval
time plus TTL, and an optional stricter local age bound. `clockSkewSeconds` defaults to
zero and must be non-negative. Callers may explicitly permit future `iat` and `nbf`
values up to that allowance; the boundary is inclusive and retains fractional seconds.
Clock skew does not extend signed expiry, TTL or the maximum age. Expiry and TTL
boundaries are exclusive; the maximum-age boundary is inclusive. Retrieving a list again
does not extend its signed expiry or reset its age from `iat`. Callers retain responsibility
for fresh retrieval, caching policy and rechecking validity after asynchronous work.

The API is in common Kotlin and uses the library's bounded JVM and portable zlib codecs.
It has no dependency on HTTP, crypto providers, OpenID4VCI or verifier policies.
