package id.walt.walletdemo.compose.logic

val VerifierDetails.displayName: String
    get() = humanReadableVerifierName(name = name, clientId = clientId, responseUri = responseUri)

fun humanReadableVerifierName(name: String?, clientId: String?, responseUri: String? = null): String {
    name?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    val response = responseUri?.trim().orEmpty()
    val parsedClientId = clientId?.let(VerifierClientIdentifier::parse)

    return parsedClientId?.host()
        ?: parsedClientId?.displayName()
        ?: NetworkOrigin.parse(response)?.host
        ?: "Unknown verifier"
}

private fun VerifierClientIdentifier.host(): String? =
    when (scheme) {
        VerifierClientIdentifier.Scheme.RedirectUri,
        VerifierClientIdentifier.Scheme.PreRegistered,
        VerifierClientIdentifier.Scheme.OpenIdFederation -> NetworkOrigin.parse(value)?.host
        else -> null
    }

private fun VerifierClientIdentifier.displayName(): String? {
    scheme.genericLabel?.let { return it }
    if (scheme == VerifierClientIdentifier.Scheme.X509SanDns) {
        return value.takeIf { it.isNotBlank() }
    }
    return rawValue.takeIf { scheme == VerifierClientIdentifier.Scheme.PreRegistered && it.length <= 48 }
}
