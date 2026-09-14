# Independent session vectors

The existing suite already covers generated cipher mutation/replay matrices, RFC 5869 HKDF,
curve agreement, malformed requests, authentication policy and wire-error disclosure suppression.
The missing oracle was a fixed external value across the complete P-256 ECDH, tagged transcript,
HKDF and directional AES-GCM boundary. `IndependentSessionVectorTest` fills that gap.

These bytes are extracted from Multipaz's public Apache-2.0 `TestVectors.kt` at release 0.100.0.
`manifest.json` pins the source URL/revision/hash and each extracted value's length/hash; `LICENSE`
and the source copyright are retained. `extract.py` accepts only that reviewed source hash and
never runs during a test. Expected bytes are not computed by the holder implementation.
All included private scalars are published example keys, exclusively for disposable test fixtures.

The four production selectors are:

| Selector | Expected oracle |
| --- | --- |
| `publicPeerVectorMatchesKeyAgreementTranscriptAndBothDirections` | Exact public request/response ciphertext and plaintext match in both directions, including the encoded SessionData envelope. |
| `publicPeerCiphertextMutationsFailClosedAndFreshControlRecovers` | Body/tag mutation, tag truncation, less than one tag, and direction reflection each fail authentication, cannot encrypt afterwards, and retain a separate valid control. |
| `publicPeerTranscriptMutationCannotAuthenticateTheCanonicalRequest` | Changing only the transcript prevents authentication; the unmodified transcript still decrypts. |
| `publicPeerCounterOneCannotBeReplayedAsCounterTwo` | Counter-one replay fails closed without increasing the successful receive count. |

Run the class on JVM, Android host tests, Kotlin/Native iOS Simulator and JS Node with the owning
module's platform test task. P-256 support is required: tests do not return early or skip a platform.
The fixture adapter imports the public PKCS#8 example keys directly into the platform ECDH
backend because Android CryptoRuntime intentionally rejects private EC imports. This tests the
production session engine and real platform crypto, without claiming application key-import coverage.
The source vectors describe conventional cipher-suite 1; the selected authorized edition-2 DIS
clause 12.2.5 was separately checked for key derivation, direction, IV/counter and tag rules.
The old example handover is opaque input for that crypto test, not edition-2 engagement evidence.

These tests do not validate the example's credential trust, application consent or issuer/device
authentication. Those production outcomes have separate SDK tests and the independent NFC reader
exchange. Their rejection exception is a cipher oracle, not a wire-status verdict or qualification
credit. No restricted standard prose or procedures are included here.
