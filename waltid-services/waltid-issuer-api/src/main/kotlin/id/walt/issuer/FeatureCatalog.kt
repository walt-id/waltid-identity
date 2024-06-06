package id.walt.issuer

import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.issuer.config.CredentialTypeConfig
import id.walt.issuer.config.OIDCIssuerServiceConfig

object FeatureCatalog : ServiceFeatureCatalog {

    val credentialTypes = BaseFeature(
        "credential-types",
        "Configure the credential types available in this issuer instance",
        mapOf("credential-issuer-metadata" to CredentialTypeConfig::class)
    )

    val issuerService = BaseFeature(
        "issuer-service",
        "Issuer Service Implementation",
        OIDCIssuerServiceConfig::class
    )

    val entra = OptionalFeature("entra", "Enable support for Microsoft Entra", default = false)

    override val baseFeatures = listOf(credentialTypes, issuerService)
    override val optionalFeatures = listOf(entra)

}
