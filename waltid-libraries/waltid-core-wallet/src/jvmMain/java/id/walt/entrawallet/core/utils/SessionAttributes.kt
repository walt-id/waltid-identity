package id.walt.entrawallet.core.utils

import id.walt.crypto.keys.Key
import id.walt.entrawallet.core.service.exchange.CredentialDataResult

object SessionAttributes {

    // TODO FIXME: openid4vc lib drops VPresentationSession attributes
    val HACK_outsideMappedSelectedCredentialsPerSession = HashMap<String, List<CredentialDataResult>>()

    // TODO FIXME: openid4vc lib drops VPresentationSession attributes
    val HACK_outsideMappedSelectedDisclosuresPerSession = HashMap<String, Map<CredentialDataResult, List<String>>>()

    val HACK_outsideMappedKey = HashMap<String, Key>()

}
