package id.walt.wallet2.handlers

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.did.dids.DidService
import id.walt.openid4vci.proofs.Proofs
import id.walt.wallet2.data.Wallet
import io.ktor.http.Url
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.test.*

class WalletIsolatedProofKeyFormsTest {
    @Test fun existingJwtHolderIdentifiersAndKeyIdFallbackRemainUsable() = runTest {
        DidService.minimalInit()
        val fixture = batchTestFixture(true)
        val holder = fixture.keys.first().crypto2Key!!
        val did = DidService.registerByKey("jwk", holder).did
        val certificate = X509CertificateUtil.createSelfSignedCertificate(holder,
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)) { subjectDn = "CN=Proof holder" }
        val headers = listOf(
            buildJsonObject { put("jwk", holder.exportPublicJwkObject()) },
            buildJsonObject { put("kid", "$did#0") },
            buildJsonObject { put("kid", did) },
            buildJsonObject { put("kid", "opaque-registered-key") },
            buildJsonObject { putJsonArray("x5c") { add(Base64.Default.encode(certificate.encodedDer.toByteArray())) } },
        )
        for ((index, header) in headers.withIndex()) for (store in listOf(false, true)) {
            val proof = CompactJws.sign("{}".encodeToByteArray(), holder, JwsAlgorithm.ES256,
                JsonObject(header + ("typ" to JsonPrimitive("openid4vci-proof+jwt"))))
            val credential = batchTestCredential(fixture.keys.first())
            batchTestClient(credential = {
                buildJsonObject { putJsonArray("credentials") { add(buildJsonObject { put("credential", credential) }) } }.toString()
            }).use { http ->
                val wallet = if (store) fixture.wallet else Wallet("stateless")
                val result = WalletIssuanceHandler.fetchCredentials(wallet,
                    FetchCredentialRequest(Url("$BATCH_TEST_ISSUER/credential"), "access", "identity",
                        proofJwt = proof, storeInWallet = store, keyId = fixture.keys.first().keyId), http)
                assertEquals(listOf(credential), result.rawCredentials, "header $index / storage $store")
                if (store) assertIs<WalletIssuanceOutcome.Stored>(result.storageOutcome)
            }
        }
        assertEquals(headers.size, fixture.store.listCredentials().toList().size)
    }

    @Test fun statelessOpaqueKidsAllowFewerCredentialsWithoutRequiringEveryProofToMatch() = runTest {
        val fixture = batchTestFixture(true)
        val proofs = fixture.keys.take(3).mapIndexed { index, entry ->
            CompactJws.sign("{}".encodeToByteArray(), entry.crypto2Key!!, JwsAlgorithm.ES256,
                buildJsonObject { put("typ", "openid4vci-proof+jwt"); put("kid", "registered-$index") })
        }
        val credential = batchTestCredential(fixture.keys[2])
        batchTestClient(credential = {
            buildJsonObject { putJsonArray("credentials") { add(buildJsonObject { put("credential", credential) }) } }.toString()
        }).use { http ->
            val result = WalletIssuanceHandler.fetchCredentials(FetchCredentialRequest(
                Url("$BATCH_TEST_ISSUER/credential"), "access", "identity", proofs = Proofs(jwt = proofs),
                credentialIssuerBaseUrl = BATCH_TEST_ISSUER), http)
            assertEquals(listOf(credential), result.rawCredentials)
        }
    }
}
