package id.walt.commons.web.modules

import id.walt.commons.featureflag.FeatureManager
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
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
        //val defaultedEnabled: FeatureFlagInformation,
        val defaultedDisabled: FeatureFlagInformation,
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
                            description = "Registered features"
                            body<Map<String, String>> {}
                        }
                    }
                }) {
                    call.respond(FeatureManager.registeredFeatures.mapValues { it.value.description })
                }
                get("state", {
                    summary = "Show state of features"
                    response {
                        HttpStatusCode.OK to {
                            description = "State of features"
                            body<FeatureFlagInformations>()
                        }
                    }
                }) {
                    val registered = FeatureManager.registeredFeatures

                    val enabled = registered.filterKeys { it in FeatureManager.enabledFeatures }.mapValues { it.value.description }
                    val disabled = registered.filterKeys { it in FeatureManager.disabledFeatures }.mapValues { it.value.description }

                    val defaulted = registered.keys
                        .subtract(enabled.keys)
                        .subtract(disabled.keys)
                        .associateWith { registered[it]!! }

                    val (defaultedEnabled, defaultedDisabled) = defaulted.entries.partition { it.value.shouldDefaultEnable() }
                        .associateBoth { (k, v) -> k to v.description }

                    call.respond(
                        FeatureFlagInformations(
                            enabled = FeatureFlagInformation(enabled),
                            disabled = FeatureFlagInformation(disabled),
                            //defaultedEnabled = FeatureFlagInformation(defaultedEnabled),
                            defaultedDisabled = FeatureFlagInformation(defaultedDisabled)
                        )
                    )
                }
            }
        }
    }

    private fun <T, X, R> Pair<Iterable<T>, Iterable<T>>.associateBoth(transform: (T) -> Pair<X, R>): Pair<Map<X, R>, Map<X, R>> = Pair(
        this.first.associate { transform(it) },
        this.second.associate { transform(it) }
    )

}
