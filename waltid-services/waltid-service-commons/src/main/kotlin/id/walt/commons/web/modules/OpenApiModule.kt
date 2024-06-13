package id.walt.commons.web.modules

import id.walt.commons.config.statics.BuildConfig
import id.walt.commons.config.statics.ServiceConfig
import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.dsl.config.OpenApiInfo
import io.github.smiley4.ktorswaggerui.dsl.config.PluginConfigDsl
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.github.smiley4.schemakenerator.core.connectSubTypes
import io.github.smiley4.schemakenerator.core.handleNameAnnotation
import io.github.smiley4.schemakenerator.reflection.collectSubTypes
import io.github.smiley4.schemakenerator.reflection.processReflection
import io.github.smiley4.schemakenerator.serialization.processKotlinxSerialization
import io.github.smiley4.schemakenerator.swagger.compileReferencingRoot
import io.github.smiley4.schemakenerator.swagger.data.TitleType
import io.github.smiley4.schemakenerator.swagger.generateSwaggerSchema
import io.github.smiley4.schemakenerator.swagger.handleCoreAnnotations
import io.github.smiley4.schemakenerator.swagger.withAutoTitle
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.nanoseconds

object OpenApiModule {

    object OpenApiConfig {
        var customInfo: (OpenApiInfo.() -> Unit)? = null

        var custom: (PluginConfigDsl.() -> Unit)? = null
    }

    // Module
    fun Application.enable() {
        install(SwaggerUI) {
            schemas {
                generator = { type ->
                    runCatching {
                        // println("Trying kotlinx schema with: $type")
                        type.processKotlinxSerialization()
                            .connectSubTypes()
                            .handleNameAnnotation()
                            .generateSwaggerSchema()
                            .handleCoreAnnotations()
                            .withAutoTitle(TitleType.SIMPLE)
                            .compileReferencingRoot()
                    }.recover { ex ->
                        // println("Falling back to reflection schema with: $type, due to $ex")
                        type
                            .collectSubTypes()
                            .processReflection()
                            .connectSubTypes()
                            .handleNameAnnotation()
                            .generateSwaggerSchema()
                            .handleCoreAnnotations()
                            .withAutoTitle(TitleType.SIMPLE)
                            .compileReferencingRoot()
                    }.getOrElse { ex ->
                        error("Could neither parse with kotlinx nor reflection: $type, due to $ex")
                    }
                }
            }

            info {
                title = "${ServiceConfig.config.vendor} ${ServiceConfig.config.name}"
                version = BuildConfig.version
                description = """
                    Interact with the ${ServiceConfig.config.vendor} ${ServiceConfig.config.name}. Version is reported to be ${BuildConfig.version} and this service instance was started ${
                    Clock.System.now().roundToSecond()
                }.
                    Questions about anything here? Visit <a href='https://github.com/walt-id/#join-the-community'>support</a>.
                    
                """.trimIndent().replace("\n", "<br/>")

                contact {
                    url = "https://walt.id"
                    name = "walt.id"
                    email = "office@walt.id"
                }
                license {
                    name = "Apache 2.0"
                    identifier = "Apache-2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0.html"
                }

                OpenApiConfig.customInfo?.invoke(this)
            }
            server {
                url = "/"
                description = "Development Server"
            }

            OpenApiConfig.custom?.invoke(this)

            externalDocs {
                url = "https://docs.walt.id"
                description = "docs.walt.id"
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

            get("/") {
                context.respondRedirect("swagger")
            }
        }
    }
}

private fun Instant.roundToSecond(): Instant =
    minus(nanosecondsOfSecond.nanoseconds)
