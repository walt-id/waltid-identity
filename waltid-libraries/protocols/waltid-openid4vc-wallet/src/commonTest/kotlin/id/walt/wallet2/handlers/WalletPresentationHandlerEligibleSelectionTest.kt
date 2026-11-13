package id.walt.wallet2.handlers

import id.walt.credentials.formats.W3C11
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.sdjwt.metadata.type.MetadataIntegrity
import id.walt.sdjwt.metadata.type.TypeMetadataFixture
import id.walt.wallet2.consent.PaymentConsentFixture
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
            val malformed = listOf("https://issuer.example/legacy", "https://issuer.example/vct").mapIndexed { index, vct ->
                StoredCredential("legacy-$index", fixture.credential(buildJsonObject {
                    put("vct", vct); put("vct#integrity", "")
                }))
            }
            val store = InMemoryCredentialStore().also {
                malformed.forEach { credential -> it.addCredential(credential) }
                it.addCredential(healthy)
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

    @Test
    fun invalidParentPinOrDocumentIdentityCannotHideAHealthyMatchOrGrantAncestry() = runTest { withContext(Dispatchers.Default) {
        val fixture = PaymentConsentFixture.create()
        val leaf = "https://issuer.example/leaf"
        val parent = "https://types.example/sca-user"
        val wrongIdentity = """{"vct":"https://wrong.example/type"}"""
        try {
            for (wrongDocument in listOf(false, true)) {
                fixture.requestUrls.clear()
                fixture.documents["/leaf"] = if (wrongDocument) wrongIdentity else TypeMetadataFixture.LEAF
                fixture.documents["/sca-user"] = TypeMetadataFixture.PARENT + " "
                val invalid = StoredCredential("invalid", fixture.credential(buildJsonObject {
                    put("vct", leaf)
                    put("vct#integrity", if (wrongDocument) MetadataIntegrity.sha256(wrongIdentity.encodeToByteArray())
                        else TypeMetadataFixture.LEAF_PIN)
                }))
                val healthy = StoredCredential("healthy", fixture.credential(buildJsonObject { put("vct", parent) }))
                val store = InMemoryCredentialStore().also {
                    it.addCredential(invalid)
                    it.addCredential(healthy)
                }
                val matches = WalletPresentationHandler.selectFromStores(
                    Wallet("invalid-chain", credentialStores = listOf(store)),
                    DcqlQuery(listOf(CredentialQuery("parent", CredentialFormat.DC_SD_JWT,
                        meta = SdJwtVcMeta(vctValues = listOf(parent)), multiple = true))),
                    useWalletCredentialIds = true, metadataHttpClient = fixture.client,
                )
                assertEquals(listOf("healthy"), matches.getValue("parent").map { (it.credential as RawDcqlCredential).id })
                assertEquals(if (wrongDocument) listOf(leaf) else listOf(leaf, parent), fixture.requestUrls)
            }
        } finally { fixture.close() }
    } }

    @Test
    fun cancellationDuringMetadataResolutionStopsWalletSelection() = runTest { withContext(Dispatchers.Default) {
        val fixture = PaymentConsentFixture.create()
        var attempted = false
        fixture.onMetadataRequest = {
            attempted = true
            throw CancellationException("Metadata selection cancelled")
        }
        try {
            val store = InMemoryCredentialStore().also {
                it.addCredential(StoredCredential("resolving", fixture.credential()))
                it.addCredential(StoredCredential("healthy", fixture.credential(buildJsonObject {
                    put("vct", "https://types.example/parent")
                })))
            }
            assertFailsWith<CancellationException> {
                WalletPresentationHandler.selectFromStores(
                    Wallet("cancelled-metadata", credentialStores = listOf(store)),
                    DcqlQuery(listOf(CredentialQuery("parent", CredentialFormat.DC_SD_JWT,
                        meta = SdJwtVcMeta(vctValues = listOf("https://types.example/parent"))))),
                    metadataHttpClient = fixture.client,
                )
            }
            assertTrue(attempted)
        } finally { fixture.close() }
    } }

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
