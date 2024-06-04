package id.walt.web.modules

import id.walt.featureflag.FeatureManager
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

object FeatureFlagInformationModule {

    @Serializable
    data class FeatureFlagInformation(
        val enabled: Map<String?, String?>,
        val total: Int
    ) {
        companion object {
            fun createCurrent(): FeatureFlagInformation {
                val enabled = FeatureManager.enabledFeatures.values.associate { it?.name to it?.description }
                return FeatureFlagInformation(enabled, enabled.size)
            }
        }
    }

    fun Application.enable() {
        routing {
            get("/features/enabled") {
                context.respond(FeatureFlagInformation.createCurrent())
            }
        }
    }

}
