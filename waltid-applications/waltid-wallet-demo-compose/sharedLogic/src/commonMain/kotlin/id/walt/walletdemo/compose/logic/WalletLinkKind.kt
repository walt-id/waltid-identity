package id.walt.walletdemo.compose.logic

import io.ktor.http.Url

/** Routing hints only. The existing protocol client still validates the resolved request. */
enum class WalletLinkKind {
    Empty, Offer, Presentation, Web, AuthorizationCallback, FidoHybrid, Unsupported;

    companion object {
        fun classify(input: String): WalletLinkKind {
            val value = input.trim()
            if (value.isEmpty()) return Empty
            val scheme = value.substringBefore(':', "").lowercase()
            if (scheme == "fido") return FidoHybrid
            if (value.any(Char::isWhitespace)) return Unsupported
            return when (scheme) {
                "openid-credential-offer" -> Offer
                "openid4vp" -> Presentation
                "openid" -> AuthorizationCallback
                "http", "https" -> {
                    if (!value.startsWith("$scheme://", ignoreCase = true)) return Unsupported
                    val authority = value.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
                    if (authority.isBlank()) return Unsupported
                    val url = runCatching { Url(value) }.getOrNull() ?: return Unsupported
                    if (url.host.isBlank()) return Unsupported
                    val parameters = url.parameters
                    when {
                        parameters.contains("code") || parameters.contains("error") -> AuthorizationCallback
                        parameters.contains("credential_offer") || parameters.contains("credential_offer_uri") -> Offer
                        parameters.contains("request") || parameters.contains("request_uri") ||
                            (parameters.contains("client_id") && (parameters.contains("dcql_query") ||
                                parameters.contains("presentation_definition") || parameters["response_type"]?.split(' ')?.contains("vp_token") == true)) -> Presentation
                        else -> Web
                    }
                }
                else -> Unsupported
            }
        }
    }
}
