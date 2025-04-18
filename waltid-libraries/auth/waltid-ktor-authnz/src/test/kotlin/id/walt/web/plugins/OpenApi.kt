package id.walt.web.plugins

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorredoc.redoc
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureOpenApi() {

    install(OpenApi) {

    }

    routing {
        route("api.json") {
            openApi()
        }

        route("swagger") {
            swaggerUI("/api.json")
        }

        route("redoc") {
            redoc("/api.json")
        }

        get("/", {
            summary = "Redirect to swagger interface for API documentation"
        }) {
            call.respondRedirect("swagger")
        }
    }

    /*install(SwaggerUI) {
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
    */
}
