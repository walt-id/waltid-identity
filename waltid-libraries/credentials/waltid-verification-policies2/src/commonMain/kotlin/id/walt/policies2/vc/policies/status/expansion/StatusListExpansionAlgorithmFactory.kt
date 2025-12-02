package id.walt.policies2.vc.policies.status.expansion

import id.walt.policies2.vc.policies.status.Base64UrlHandler
import id.walt.policies2.vc.policies.status.Values.BITSTRING_STATUS_LIST
import id.walt.policies2.vc.policies.status.Values.REVOCATION_LIST_2020
import id.walt.policies2.vc.policies.status.Values.STATUS_LIST_2021
import id.walt.policies2.vc.policies.status.model.StatusContent
import id.walt.policies2.vc.policies.status.model.W3CStatusContent

interface StatusListExpansionAlgorithmFactory<T : StatusContent> {
    fun create(content: T): id.walt.policies2.vc.policies.status.expansion.StatusListExpansionAlgorithm
}

class W3cStatusListExpansionAlgorithmFactory(
    private val base64UrlHandler: Base64UrlHandler
) : id.walt.policies2.vc.policies.status.expansion.StatusListExpansionAlgorithmFactory<W3CStatusContent> {

    override fun create(content: W3CStatusContent): id.walt.policies2.vc.policies.status.expansion.StatusListExpansionAlgorithm =
        when (content.type) {
            BITSTRING_STATUS_LIST -> _root_ide_package_.id.walt.policies2.vc.policies.status.expansion.BitstringStatusListExpansionAlgorithm(
                base64UrlHandler
            )

            STATUS_LIST_2021 -> _root_ide_package_.id.walt.policies2.vc.policies.status.expansion.StatusList2021ExpansionAlgorithm()
            REVOCATION_LIST_2020 -> _root_ide_package_.id.walt.policies2.vc.policies.status.expansion.RevocationList2020ExpansionAlgorithm()
            else -> throw IllegalArgumentException("W3C status type not supported: $content")
        }
}
