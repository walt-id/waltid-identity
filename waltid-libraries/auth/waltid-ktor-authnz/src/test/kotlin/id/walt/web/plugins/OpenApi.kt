package id.walt.web.plugins

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.reflect.KType

fun Application.configureOpenApi() {
    install(SwaggerUI) {
        swagger {
//            swaggerUrl = "swagger-ui"
//            forwardRoot = true
        }
        info {
            title = "Example API"
            version = "latest"
            description = "Example API for testing and demonstration purposes."
        }
        server {
            url = "/"
            description = "Development Server"
        }
        swagger {
            showTagFilterInput = true
        }
    }
    routing {
        route("swagger") {
            swaggerUI("/api.json")
        }

        route("api.json") {
            openApiSpec()
        }

        get("/", {
            summary = "Redirect to swagger interface for API documentation"
        }) {
            context.respondRedirect("swagger")
        }
    }
}
