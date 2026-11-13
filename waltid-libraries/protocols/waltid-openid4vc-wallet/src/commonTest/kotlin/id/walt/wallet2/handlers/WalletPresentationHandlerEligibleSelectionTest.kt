package id.walt.wallet2.handlers

import id.walt.credentials.formats.W3C11
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.wallet2.consent.PaymentConsentFixture
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletPresentationHandlerEligibleSelectionTest {

    /**
     * Credential Manager evaluates each registry entry independently, so two same-format
     * credentials both appear even when DCQL defaults to `multiple=false`. Rematching the
     * whole store would keep only the first hit. Restricting candidates to the selected
     * credential must keep that one presentable.
     */
    @Test
    fun selectFromStoresKeepsTheEligibleMatchWhenMultipleIsFalse() = runTest {
        val wallet = Wallet(
            id = "eligible-selection",
            credentialStores = listOf(
                InMemoryCredentialStore().also { store ->
                    store.addCredential(storedCredential("pid-1"))
                    store.addCredential(storedCredential("pid-2"))
                },
            ),
        )
        val query = DcqlQuery(
            credentials = listOf(
                CredentialQuery(
                    id = "pid",
                    format = CredentialFormat.JWT_VC_JSON,
                    meta = NoMeta,
                )
            )
        )

        val rematched = WalletPresentationHandler.selectFromStores(
            wallet = wallet,
            query = query,
            useWalletCredentialIds = true,
            eligibleCredentialIds = setOf("pid-2"),
        )

        assertEquals(
            "pid-2",
            (rematched.getValue("pid").single().credential as RawDcqlCredential).id,
        )
    }

    @Test
    fun malformedMetadataNeighbourDoesNotBlockAHealthyExactTypeMatch() = runTest {
        val fixture = PaymentConsentFixture.create()
        try {
            val healthy = StoredCredential("healthy", fixture.credential())
            val malformed = StoredCredential("legacy", fixture.credential(buildJsonObject {
                put("vct", "https://issuer.example/legacy"); put("vct#integrity", "")
            }))
            val store = InMemoryCredentialStore().also {
                it.addCredential(malformed); it.addCredential(healthy)
            }
            val query = DcqlQuery(credentials = listOf(CredentialQuery(
                id = "payment", format = CredentialFormat.DC_SD_JWT,
                meta = SdJwtVcMeta(vctValues = listOf("https://issuer.example/vct")),
            )))
            val matches = WalletPresentationHandler.selectFromStores(
                wallet = Wallet(id = "mixed-metadata", credentialStores = listOf(store)),
                query = query, useWalletCredentialIds = true,
            )
            assertEquals(listOf("healthy"), matches.getValue("payment").map { (it.credential as RawDcqlCredential).id })
        } finally { fixture.close() }
    }

    private fun storedCredential(id: String): StoredCredential = StoredCredential(
        id = id,
        credential = W3C11(
            credentialData = buildJsonObject {
                put("credentialSubject", buildJsonObject { put("given_name", "Ada") })
            },
            issuer = "https://issuer.example",
            subject = "did:example:holder",
            signature = JwtCredentialSignature("signature", buildJsonObject {}),
            signed = "issuer.jwt.signature",
        ),
    )
}
