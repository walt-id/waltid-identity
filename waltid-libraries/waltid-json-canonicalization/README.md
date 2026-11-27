# Shared JSON canonicalization

`id.walt.json.JsonCanonicalization` implements RFC 8785 for DID and JAdES. It sorts object keys by
UTF-16 code units, preserves array order, rejects duplicate member names and lone surrogates, and
serializes binary64 numbers using ECMAScript-compatible formatting. Large exact integers should be
encoded as strings, as RFC 8785 describes.

JVM and Android use the existing Apache-licensed JCS number serializer. JavaScript uses its native
number serialization. iOS uses an isolated JavaScriptCore context per canonicalization operation;
contexts are not shared across threads. The common test suite includes all finite Appendix B number
vectors and invalid-input cases. JVM and Node run on Linux; native tests require an Apple runner.

The existing `id.walt.did.utils.JsonCanonicalization` entry points delegate here for compatibility.
The shared duplicate-member scanner is also used by OpenID4VCI before decoding request parameters.
