package id.walt.wallet.core.utils

object SessionAttributes {

    // TODO FIXME: openid4vc lib drops VPresentationSession attributes
    val HACK_outsideMappedSelectedCredentialsPerSession = HashMap<String, List<String>>()

    // TODO FIXME: openid4vc lib drops VPresentationSession attributes
    val HACK_outsideMappedSelectedDisclosuresPerSession = HashMap<String, Map<String, List<String>>>()

}
