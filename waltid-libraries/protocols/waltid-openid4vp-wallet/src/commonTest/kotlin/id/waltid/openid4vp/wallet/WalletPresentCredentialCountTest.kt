package id.waltid.openid4vp.wallet

import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletPresentCredentialCountTest {

    @Test
    fun `one selected credential is counted from many isolated candidates`() {
        val query = DcqlQuery(credentials = listOf(credentialQuery("identity")))
        val candidates = List(3) { credential("credential-$it") }

        val matches = DcqlMatcher.match(query, candidates).getOrThrow()

        assertEquals(1, WalletPresentFunctionality2.distinctCredentialCount(matches))
    }

    @Test
    fun `credential matching multiple queries is counted once`() {
        val query = DcqlQuery(
            credentials = listOf(
                credentialQuery("identity"),
                credentialQuery("age"),
            ),
        )

        val matches = DcqlMatcher.match(query, listOf(credential("credential"))).getOrThrow()

        assertEquals(2, matches.size)
        assertEquals(1, WalletPresentFunctionality2.distinctCredentialCount(matches))
    }

    private fun credentialQuery(id: String) = CredentialQuery(
        id = id,
        format = CredentialFormat.JWT_VC_JSON,
        meta = NoMeta,
    )

    private fun credential(id: String) = RawDcqlCredential(
        id = id,
        format = CredentialFormat.JWT_VC_JSON.id.first(),
        data = buildJsonObject {},
    )
}
