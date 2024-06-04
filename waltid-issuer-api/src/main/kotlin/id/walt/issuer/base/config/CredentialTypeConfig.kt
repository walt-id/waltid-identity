package id.walt.issuer.base.config

data class CredentialTypeConfig(
    val supportedCredentialTypes: Map<String, List<String>>
) : BaseConfig
