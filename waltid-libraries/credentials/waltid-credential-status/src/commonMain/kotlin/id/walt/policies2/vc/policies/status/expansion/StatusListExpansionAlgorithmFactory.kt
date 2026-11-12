package id.walt.policies2.vc.policies.status.expansion

import id.walt.policies2.vc.policies.status.Base64UrlHandler
import id.walt.policies2.vc.policies.status.Values.BITSTRING_STATUS_LIST
import id.walt.policies2.vc.policies.status.Values.REVOCATION_LIST_2020
import id.walt.policies2.vc.policies.status.Values.STATUS_LIST_2021
import id.walt.policies2.vc.policies.status.model.StatusContent
import id.walt.policies2.vc.policies.status.model.W3CStatusContent

interface StatusListExpansionAlgorithmFactory<T : StatusContent> {
    fun create(content: T): StatusListExpansionAlgorithm
}

class W3cStatusListExpansionAlgorithmFactory(
    private val base64UrlHandler: Base64UrlHandler
) : StatusListExpansionAlgorithmFactory<W3CStatusContent> {

    override fun create(content: W3CStatusContent): StatusListExpansionAlgorithm =
        when (content.type) {
            BITSTRING_STATUS_LIST -> BitstringStatusListExpansionAlgorithm(
                base64UrlHandler
            )

            STATUS_LIST_2021 -> StatusList2021ExpansionAlgorithm()
            REVOCATION_LIST_2020 -> RevocationList2020ExpansionAlgorithm()
            else -> throw IllegalArgumentException("W3C status type not supported: $content")
        }
}
