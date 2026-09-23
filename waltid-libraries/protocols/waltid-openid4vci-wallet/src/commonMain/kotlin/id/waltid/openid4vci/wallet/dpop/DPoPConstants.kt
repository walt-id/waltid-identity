package id.waltid.openid4vci.wallet.dpop

/**
 * Wire-level constants for RFC 9449 DPoP.
 *
 * Top-level rather than private to a single builder so that every layer speaking DPoP - the token
 * request builder here, and the wallet issuance handlers that make protected-resource requests -
 * shares one definition instead of restating the literals.
 */

/** Request header carrying the DPoP proof JWT (RFC 9449 §4). */
const val DPOP_HEADER = "DPoP"

/** Response header through which a server supplies a nonce to include in the next proof (§8). */
const val DPOP_NONCE_HEADER = "DPoP-Nonce"

/** OAuth error code demanding that the request be retried with a server-supplied nonce (§8-9). */
const val USE_DPOP_NONCE = "use_dpop_nonce"

/**
 * Total attempts for a DPoP-protected request: the first, plus one retry after the server supplies
 * a nonce. RFC 9449 expects a single retry - a server that demands a new nonce again is misbehaving
 * and must not be looped on.
 */
const val DPOP_NONCE_ATTEMPTS = 2
