import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.healthApi() {
    routing {
        route("healthz", {
            tags = listOf("ServiceHealth")
        }) {
            get({
                summary = "Service health status"
                response {
                    HttpStatusCode.OK to {
                        description = "Service health status"
                    }
                }
            }) {
                context.respond(HttpStatusCode.OK)
            }
        }
    }
}