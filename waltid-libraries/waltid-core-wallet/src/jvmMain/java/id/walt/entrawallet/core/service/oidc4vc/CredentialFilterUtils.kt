package id.walt.corewallet.service.oidc4vc

import id.walt.corewallet.service.exchange.CredentialDataResult
import id.walt.oid4vc.data.CredentialFormat

object CredentialFilterUtils {

    fun getJwtVcList(credentials: List<CredentialDataResult>, disclosures: Map<String, List<String>>?) =
        credentials.filter {
            setOf(CredentialFormat.jwt_vc, CredentialFormat.jwt_vc_json).contains(it.format)
        }.map {
            if (disclosures?.containsKey(it.id) == true) {
                it.document + "~${disclosures[it.id]!!.joinToString("~")}"
            } else {
                it.document
            }
        }
}
