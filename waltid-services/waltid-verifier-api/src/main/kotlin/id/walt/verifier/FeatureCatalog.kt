package id.walt.verifier

import id.walt.commons.config.list.DevModeConfig
import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.verifier.config.OIDCVerifierServiceConfig
import id.walt.verifier.entra.EntraConfig

object FeatureCatalog : ServiceFeatureCatalog {

    private val verifierService = BaseFeature("verifier-service", "Verifier Service Implementation", OIDCVerifierServiceConfig::class)

    val entra = OptionalFeature("entra", "Enable Microsoft Entra support", EntraConfig::class, false)

    val devModeFeature = OptionalFeature("dev-mode", "Development mode", DevModeConfig::class, default = false)

    override val baseFeatures = listOf(verifierService)
    override val optionalFeatures: List<OptionalFeature> = listOf(entra, devModeFeature)
}
