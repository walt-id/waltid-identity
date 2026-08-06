package id.walt.verifier2.handlers.sessioncreation

private const val DECENTRALIZED_IDENTIFIER_PREFIX = "decentralized_identifier:"

/** Builds the standards-level `kid` used by a signed verifier Request Object. */
internal fun requestObjectKid(clientId: String?, keyId: String): String =
    clientId
        ?.takeIf { it.startsWith(DECENTRALIZED_IDENTIFIER_PREFIX) }
        ?.substringAfter(DECENTRALIZED_IDENTIFIER_PREFIX)
        ?.takeIf { it.isNotBlank() }
        ?.let { did -> if ('#' in did) did else "$did#$keyId" }
        ?: keyId
