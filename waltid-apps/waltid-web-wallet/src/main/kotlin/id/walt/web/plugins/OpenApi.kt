package id.walt.web.plugins

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

        securityScheme("authenticated-session") {
            type = AuthType.API_KEY
            location = AuthKeyLocation.COOKIE
        }

        securityScheme("authenticated-bearer") {
            type = AuthType.API_KEY
            location = AuthKeyLocation.HEADER
            scheme = AuthScheme.BEARER
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
