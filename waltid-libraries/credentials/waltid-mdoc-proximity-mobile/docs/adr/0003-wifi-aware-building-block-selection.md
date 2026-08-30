# ADR 0003: Wi-Fi Aware building-block selection

- Status: implementation draft; physical interoperability qualification pending
- Ticket: WAL-1348
- Date: 2026-08-31

## Context

ISO/IEC 18013-5 edition 2 defines holder-side Wi-Fi Aware retrieval as a transaction-bound NAN
publisher, encrypted NAN data-path responder, TCP server, and strict HTTP `/mdoc` endpoint. The
service name and default shared-key passphrase derive from the exact transaction `EDeviceKeyBytes`.
The reader is the NAN subscriber, data-path initiator, and HTTP client.

The implementation must preserve the existing common proximity engine's ownership: transport code
exchanges bounded opaque mdoc messages and does not parse requests, select credentials, perform
reader trust, or introduce an OpenID/server-retrieval path.

## Decision

Implement the mandatory NCS-SK-128 holder baseline as independently authored common Kotlin and a
thin Android public-API adapter:

- common code derives `NANService` and `NANPassphrase`, validates Wi-Fi Aware carriers and band
  intersections, frames sequential HTTP/1.1 exchanges, normalizes failures, and owns timeouts and
  deterministic cleanup;
- Android API 33 is the minimum because explicit data-path cipher selection first becomes available
  through `WifiAwareDataPathSecurityConfig` there;
- Android prepares the holder as publisher, NDP responder, and TCP server; it sets NCS-SK-128 on
  both the publish configuration and network specifier and passes the server port and TCP protocol
  through the secure data-path request;
- capability checks remain side-effect-free. Values that Android may expose only after `attach`
  are rechecked before any method is advertised;
- only the mandatory 2.4 GHz NAN band is advertised. Android's public API exposes general 5 GHz
  support but no pre-data-path proof that the Aware implementation supports that optional band;
- Android 13+ requires the host-owned `NEARBY_WIFI_DEVICES` runtime grant. Apps targeting Android
  17/API 37 also require `ACCESS_LOCAL_NETWORK`. The SDK reports separate remediation actions and
  never prompts;
- iOS reports `implemented = false`. Apple's public Wi-Fi Aware API requires prior device pairing
  and an app-declared DNS-SD service name of at most 15 characters containing a letter; it cannot
  publish ISO's fresh 32-character hexadecimal transaction service name;
- NCS-PK-2WDH-128 is not advertised or silently downgraded. It remains gated on the canonical NAN
  3.1 key/carrier contract and an independent NFC Negotiated Handover implementation and physical
  proof.

QR Device Engagement omits the passphrase so both peers derive it from `EDeviceKeyBytes`. NFC
Static or Negotiated Handover carries the selected NCS-SK passphrase explicitly in the Wi-Fi Aware
carrier. One prepared publisher is reference-counted when concurrent QR and NFC engagement paths
need representations of the same transaction endpoint.

## Source and implementation review

The selected protocol source is the authorized ISO/IEC DIS 18013-5 edition-2 copy, clauses 9.2,
9.3, and 11.3. Wi-Fi Alliance Wi-Fi Aware 3.2 section 12 and its carrier table corroborate the
NCS-SK carrier field layout and mandatory 2.4 GHz NAN baseline; the DIS's normative NAN 3.1 source
remains the release gate for NCS-PK details.

The following primary platform references govern the adapter rather than the protocol contract:

- [Android Wi-Fi Aware overview](https://developer.android.com/develop/connectivity/wifi/wifi-aware)
- [Android Wi-Fi Aware characteristics](https://developer.android.com/reference/android/net/wifi/aware/Characteristics)
- [Android explicit data-path security](https://developer.android.com/reference/android/net/wifi/aware/WifiAwareDataPathSecurityConfig.Builder)
- [Android Wi-Fi Aware network specifier](https://developer.android.com/reference/android/net/wifi/aware/WifiAwareNetworkSpecifier.Builder)
- [Android nearby Wi-Fi permission](https://developer.android.com/develop/connectivity/wifi/wifi-permissions)
- [Android local-network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- [Apple adopting Wi-Fi Aware](https://developer.apple.com/documentation/wifiaware/adopting-wi-fi-aware)
- [Apple paired-device connection model](https://developer.apple.com/documentation/wifiaware/connecting-paired-devices)
- [RFC 5869 HKDF](https://www.rfc-editor.org/rfc/rfc5869) and
  [RFC 4648 base64url](https://www.rfc-editor.org/rfc/rfc4648)

[Multipaz at `7c0988bee3384d13a0732e0c33336ae0faf3b863`](https://github.com/openwallet-foundation/multipaz/commit/7c0988bee3384d13a0732e0c33336ae0faf3b863)
was reviewed as a pinned lifecycle and NCS-SK differential reference. No source was copied. Its
legacy blocking/threading, implicit cipher selection, model hierarchy, logging, and incomplete
NCS-PK behavior were not adopted.

## Consequences and qualification boundary

The implementation has deterministic host coverage for derivation vectors, carrier validation,
HTTP framing, provider sharing, error handling, SDK fallback, and platform capability mapping. It
does not establish physical Wi-Fi Aware interoperability. Before release qualification, record
successful QR, NFC Static, and NFC Negotiated NCS-SK exchanges with a separate Android reader and
verify permission denial/retry, radio/resource loss, peer disconnect, timeout, cancellation, repeat
sessions, and no leaked Aware sessions, network callbacks, receivers, or sockets.

The draft must not claim NCS-PK, iOS Wi-Fi Aware, optional 5 GHz support, server retrieval, or broad
ISO/EUDI conformance. Revisit this decision when Apple exposes an unpaired dynamic-service API,
Android exposes source-backed pre-data-path Aware bands, or the canonical NAN 3.1 source and
independent NCS-PK reader are available.
