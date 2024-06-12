package id.walt.webwallet.web.plugins

import id.walt.commons.web.modules.OpenApiModule
import io.github.smiley4.ktorswaggerui.data.AuthKeyLocation
import io.github.smiley4.ktorswaggerui.data.AuthScheme
import io.github.smiley4.ktorswaggerui.data.AuthType
import io.ktor.server.plugins.ratelimit.*

internal val walletOpenApiPluginAmendment: suspend () -> Unit = suspend {
    OpenApiModule.OpenApiConfig.apply {
        customInfo = {
            description +=
                "\nAny HTTP status code of 200 - 299 indicates request success, 400 - 499 client error / invalid request, 500+ internal server processing exception."
                    .replace("\n", "<br/>")
        }

        custom = {
            ignoredRouteSelectors += RateLimitRouteSelector::class // Do not break schemas due to rate limiting overlay

            security {
                securityScheme("auth-session") {
                    name = "Session-Cookie Authentication"
                    type = AuthType.API_KEY
                    location = AuthKeyLocation.COOKIE
                }

                securityScheme("auth-bearer") {
                    name = "Bearer token authentication"
                    description = "Set as \"Authorization: Bearer %token-here%\" to authenticate."
                    scheme = AuthScheme.BEARER
                    type = AuthType.HTTP
                }

                securityScheme("auth-bearer-alternative") {
                    name = "Bearer token authentication (alternative header)"
                    description = "Set alternative header \"waltid-authorization: Bearer %token-here%\" to authenticate."
                    scheme = AuthScheme.BEARER
                    type = AuthType.HTTP
                    location = AuthKeyLocation.HEADER
                }

                defaultUnauthorizedResponse {
                    description = "Invalid authentication"
                }
            }
        }
    }
}
