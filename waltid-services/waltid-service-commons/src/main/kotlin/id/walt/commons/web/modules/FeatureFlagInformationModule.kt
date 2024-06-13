package id.walt.commons.web.modules

import id.walt.commons.featureflag.FeatureManager
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

object FeatureFlagInformationModule {

    @Serializable
    data class FeatureFlagInformations(
        val enabled: FeatureFlagInformation,
        val disabled: FeatureFlagInformation,
        val defaulted: FeatureFlagInformation,
    )

    @Serializable
    data class FeatureFlagInformation(
        val features: Map<String, String>,
        val total: Int = features.size,
    )

    fun Application.enable() {
        routing {
            route("features", {
                tags = listOf("Feature management")
            }) {
                get("registered", {
                    summary = "List registered features"
                    response {
                        HttpStatusCode.OK to {
                            body<Map<String, String>> {
                                description = "Registered features"
                            }
                        }
                    }
                }) {
                    context.respond(FeatureManager.registeredFeatures.mapValues { it.value.description })
                }
                get("state", {
                    summary = "Show state of features"
                    response {
                        HttpStatusCode.OK to {
                            body<FeatureFlagInformations>()
                        }
                    }
                }) {
                    val registered = FeatureManager.registeredFeatures

                    val enabled = registered.filterKeys { it in FeatureManager.enabledFeatures }.mapValues { it.value.description }
                    val disabled = registered.filterKeys { it in FeatureManager.disabledFeatures }.mapValues { it.value.description }

                    val defaulted = registered.keys.subtract(enabled.keys).subtract(disabled.keys)
                        .associateWith { registered[it]!!.description }

                    context.respond(
                        FeatureFlagInformations(
                            enabled = FeatureFlagInformation(enabled),
                            disabled = FeatureFlagInformation(disabled),
                            defaulted = FeatureFlagInformation(defaulted)
                        )
                    )
                }
            }
        }
    }

}
