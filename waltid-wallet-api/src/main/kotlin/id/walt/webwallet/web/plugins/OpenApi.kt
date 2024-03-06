package id.walt.webwallet.web.plugins

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.data.AuthKeyLocation
import io.github.smiley4.ktorswaggerui.data.AuthScheme
import io.github.smiley4.ktorswaggerui.data.AuthType
import io.ktor.server.application.*

fun Application.configureOpenApi() {
    install(SwaggerUI) {
        swagger {
            swaggerUrl = "swagger"
            forwardRoot = true
        }
        info {
            title = "walt.id wallet API"
            version = "latest"
            description = """
                Interact with the wallet backend.
                Any HTTP status code of 200 - 299 indicates request success, 400 - 499 client error / invalid request, 500+ internal server processing exception.
            """.trimIndent().replace("\n", "<br/>")
        }

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

        externalDocs {
            url = "https://docs.walt.id"
            description = "docs.walt.id"
        }
    }
}
