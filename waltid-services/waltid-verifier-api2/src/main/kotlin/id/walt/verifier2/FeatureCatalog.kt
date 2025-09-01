package id.walt.verifier2

import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog

object FeatureCatalog : ServiceFeatureCatalog {

    private val verifierService =
        BaseFeature("verifier-service", "Verifier Service Implementation", OSSVerifier2ServiceConfig::class)

    //val entra = OptionalFeature("entra", "Enable Microsoft Entra support", EntraConfig::class, false)

    override val baseFeatures = listOf(verifierService)
    override val optionalFeatures: List<OptionalFeature> = listOf()
}
