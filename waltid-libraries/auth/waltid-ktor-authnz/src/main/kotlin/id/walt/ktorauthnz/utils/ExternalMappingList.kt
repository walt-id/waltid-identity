package id.walt.ktorauthnz.utils

import id.walt.ktorauthnz.methods.OIDC

object ExternalMappingList {

    val ALL_EXTERNAL_MAPPINGS: List<String> = listOf(
        OIDC.OIDC_STATE_NAMESPACE,
        OIDC.OIDC_SESSION_NAMESPACE,
    )

}
