package id.walt.issuer

import id.walt.commons.config.list.DevModeConfig
import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.issuer.config.AuthenticationServiceConfig
import id.walt.issuer.config.CredentialTypeConfig
import id.walt.issuer.config.OIDCIssuerServiceConfig

object FeatureCatalog : ServiceFeatureCatalog {

    private val credentialTypes = BaseFeature(
        "credential-types",
        "Configure the credential types available in this issuer instance",
        mapOf("credential-issuer-metadata" to CredentialTypeConfig::class)
    )

    private val issuerService = BaseFeature(
        "issuer-service",
        "Issuer Service Implementation",
        OIDCIssuerServiceConfig::class
    )

    private val authenticationService = BaseFeature(
        "authentication-service",
        "Authentication Service Implementation",
        AuthenticationServiceConfig::class
    )

    val entra = OptionalFeature("entra", "Enable support for Microsoft Entra", default = false)

    val devModeFeature = OptionalFeature("dev-mode", "Development mode", DevModeConfig::class, default = false)

    override val baseFeatures = listOf(credentialTypes, issuerService, authenticationService)
    override val optionalFeatures = listOf(entra, devModeFeature)

}
