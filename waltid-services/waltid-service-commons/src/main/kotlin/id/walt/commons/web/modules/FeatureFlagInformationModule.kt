package id.walt.commons.web.modules

import id.walt.commons.featureflag.FeatureManager
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun featureBucketExample(vararg features: Pair<String, String>) = buildJsonObject {
    put("features", buildJsonObject {
        features.forEach { (name, description) -> put(name, description) }
    })
    put("total", features.size)
}

internal val registeredFeaturesExample = buildJsonObject {
    put("web", "Web service")
    put("openapi", "Enables openapi endpoints")
    put("persistence", "Storage")
}

internal val featureStateExample = buildJsonObject {
    put(
        "enabled",
        featureBucketExample(
            "web" to "Web service",
            "openapi" to "Enables openapi endpoints",
        ),
    )
    put(
        "disabled",
        featureBucketExample(
            "debug-endpoints" to "Enables various debug endpoints",
        ),
    )
    put(
        "defaultedDisabled",
        featureBucketExample(
            "persistence" to "Storage",
        ),
    )
}

internal val registeredFeaturesDescription = """
    Lists every feature flag registered by this service instance, regardless of its current state.
    The response is an object whose keys are stable feature identifiers and whose values are
    human-readable descriptions. Use it to discover valid identifiers before changing
    `enabledFeatures` or `disabledFeatures` in feature configuration.

    This endpoint is intentionally public and requires no authentication. It exposes feature names
    and descriptions, but never feature configuration values or secrets. Feature descriptions must
    therefore not contain sensitive information.
""".trimIndent()

internal val featureStateDescription = """
    Shows how every registered feature is resolved at runtime. The three buckets are mutually
    exclusive:

    - `enabled`: features successfully enabled during startup. This includes base features,
      explicitly enabled features, and features whose declared default is enabled.
    - `disabled`: features explicitly listed in `disabledFeatures` configuration.
    - `defaultedDisabled`: features that were not explicitly configured and whose declared default
      is disabled.

    Each bucket contains `features`, a map from stable feature identifiers to human-readable
    descriptions, and `total`, the number of entries in that map. There is intentionally no
    `defaultedEnabled` bucket: default-enabled features are activated during startup and reported
    under `enabled`.

    This endpoint is intentionally public and requires no authentication. It is intended for
    diagnostics and exposes the service's feature posture, so deployments should account for that
    operational metadata exposure. It does not expose configuration values or secrets and must not
    be used as an authorization decision point.
""".trimIndent()

object FeatureFlagInformationModule {

    /**
     * Mutually exclusive feature-state buckets.
     *
     * Features that default to enabled are enabled during startup and are therefore included in [enabled].
     * Only features that remain disabled by default need a separate [defaultedDisabled] bucket.
     */
    @Serializable
    data class FeatureFlagInformations(
        /** Features that were successfully enabled, including base, explicitly enabled, and default-enabled features. */
        val enabled: FeatureFlagInformation,

        /** Features explicitly disabled through feature configuration. */
        val disabled: FeatureFlagInformation,

        /** Features not explicitly configured whose declared default is disabled. */
        val defaultedDisabled: FeatureFlagInformation,
    )

    @Serializable
    data class FeatureFlagInformation(
        /** Map of stable feature identifiers to their human-readable descriptions. */
        val features: Map<String, String>,

        /** Number of entries in [features]. */
        val total: Int = features.size,
    )

    fun Application.enable() {
        routing {
            route("features", {
                tags = listOf("Feature management")
            }) {
                get("registered", {
                    summary = "List registered features"
                    description = registeredFeaturesDescription
                    response {
                        HttpStatusCode.OK to {
                            description = "All feature identifiers registered by this service and their descriptions"
                            body<Map<String, String>> {
                                required = true
                                example("Registered feature catalog") {
                                    value = registeredFeaturesExample
                                }
                            }
                        }
                    }
                }) {
                    call.respond(FeatureManager.registeredFeatures.mapValues { it.value.description })
                }
                get("state", {
                    summary = "Show feature states"
                    description = featureStateDescription
                    response {
                        HttpStatusCode.OK to {
                            description = "Runtime state of every registered feature"
                            body<FeatureFlagInformations> {
                                required = true
                                example("Feature state buckets") {
                                    value = featureStateExample
                                }
                            }
                        }
                    }
                }) {
                    call.respond(currentFeatureState())
                }
            }
        }
    }

    internal fun currentFeatureState(): FeatureFlagInformations {
        val registered = FeatureManager.registeredFeatures
        val enabled = registered
            .filterKeys { it in FeatureManager.enabledFeatures }
            .mapValues { it.value.description }
        val disabled = registered
            .filterKeys { it in FeatureManager.disabledFeatures }
            .mapValues { it.value.description }
        val defaultedDisabled = registered
            .filterKeys { it !in enabled && it !in disabled }
            .filterValues { !it.shouldDefaultEnable() }
            .mapValues { it.value.description }

        return FeatureFlagInformations(
            enabled = FeatureFlagInformation(enabled),
            disabled = FeatureFlagInformation(disabled),
            defaultedDisabled = FeatureFlagInformation(defaultedDisabled),
        )
    }

}
