package id.walt.web.plugins

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.dsl.config.PluginConfigDsl
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureRouting() {
    install(AutoHeadResponse)
    install(DoubleReceive)
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
            description = "Development Server"
        }
        specAssigner = { _, _ -> PluginConfigDsl.DEFAULT_SPEC_ID }
        spec("api") {
            specAssigner = { _, _ -> PluginConfigDsl.DEFAULT_SPEC_ID }
        }
    }

    /*routing {
        route("swagger") {
            swaggerUI("/api.json")
        }
        route("api.json") {
            println("specs: " + ApiSpec.getAll())
            openApiSpec()
        }


        get("/", {
            summary = "Redirect to swagger interface for API documentation"
        }) {
            context.respondRedirect("swagger")
        }
    }*/

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
}
