package id.walt.verifier.web.plugins

import id.walt.commons.config.buildconfig.BuildConfig
import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.data.AuthKeyLocation
import io.github.smiley4.ktorswaggerui.data.AuthType
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureOpenApi() {
    routing {
        route("swagger") {
            swaggerUI("/api.json")
        }

        route("api.json") {
            openApiSpec()
        }

        get("/") {
            context.respondRedirect("swagger")
        }
    }

    install(SwaggerUI) {
        info {
            title = "walt.id Verifier API"
            version = BuildConfig.version
            description = "Interact with the walt.id verifier"
        }
        server {
            url = "/"
            description = "Development Server"
        }

        /*security {
            securityScheme("authenticated") {
                type = AuthType.API_KEY
                location = AuthKeyLocation.COOKIE
            }

            defaultUnauthorizedResponse {
                description = "Invalid authentication"
            }
        }*/

        externalDocs {
            url = "https://docs.walt.id"
            description = "docs.walt.id"
        }
    }
}
