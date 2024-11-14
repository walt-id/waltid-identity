package id.walt.wallet.core.utils

import id.walt.crypto.keys.Key

object SessionAttributes {

    // TODO FIXME: openid4vc lib drops VPresentationSession attributes
    val HACK_outsideMappedSelectedCredentialsPerSession = HashMap<String, List<WalletCredential>>()

    // TODO FIXME: openid4vc lib drops VPresentationSession attributes
    val HACK_outsideMappedSelectedDisclosuresPerSession = HashMap<String, Map<WalletCredential, List<String>>>()

    val HACK_outsideMappedKey = HashMap<String, Key>()

}
