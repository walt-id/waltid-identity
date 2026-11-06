package id.walt.wallet2.handlers

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto.keys.Key
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.DidResolver
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

    @Test fun didAndCertificateProofsRejectUnselectedKeysBeforeIssuance() = runTest {
        DidService.minimalInit()
        val fixture = batchTestFixture(true)
        val holder = fixture.keys.first().crypto2Key!!
        val did = DidService.registerByKey("jwk", holder).did
        val certificate = X509CertificateUtil.createSelfSignedCertificate(holder,
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)) { subjectDn = "CN=Proof holder" }
        for (header in listOf(
            buildJsonObject { put("kid", "$did#0") },
            buildJsonObject { putJsonArray("x5c") { add(Base64.Default.encode(certificate.encodedDer.toByteArray())) } },
        )) {
            val proof = CompactJws.sign("{}".encodeToByteArray(), holder, JwsAlgorithm.ES256, header)
            var requests = 0
            batchTestClient(credential = { requests++; error("Must reject the storage selection first") }).use { http ->
                assertFailsWith<IllegalArgumentException> {
                    WalletIssuanceHandler.fetchCredentials(fixture.wallet,
                        FetchCredentialRequest(Url("$BATCH_TEST_ISSUER/credential"), "access", "identity",
                            proofJwt = proof, storeInWallet = true, keyId = fixture.keys[1].keyId), http)
                }
            }
            assertEquals(0, requests)
            assertTrue(fixture.store.listCredentials().toList().isEmpty())
        }
    }

    @Test fun issuerChosenSubjectAndConfirmationDidsNeverTriggerResolution() = runTest {
        val fixture = batchTestFixture(true)
        var resolutions = 0
        val previous = DidService.registerResolverForMethod("web", object : DidResolver {
            override val name = "Unexpected DID lookup detector"
            override suspend fun getSupportedMethods() = Result.success(setOf("web"))
            override suspend fun resolve(did: String): Result<JsonObject> { resolutions++; return Result.failure(IllegalStateException("Unexpected lookup")) }
            override suspend fun resolveToKey(did: String): Result<Key> { resolutions++; return Result.failure(IllegalStateException("Unexpected lookup")) }
            override suspend fun resolveToKeys(did: String): Result<Set<Key>> { resolutions++; return Result.failure(IllegalStateException("Unexpected lookup")) }
        })
        try {
            for (opaque in listOf(false, true)) for (subject in listOf(false, true)) {
                val holder = fixture.keys.first().crypto2Key!!
                val proof = CompactJws.sign("{}".encodeToByteArray(), holder, JwsAlgorithm.ES256,
                    buildJsonObject {
                        put("typ", "openid4vci-proof+jwt")
                        if (opaque) put("kid", "registered-holder") else put("jwk", holder.exportPublicJwkObject())
                    })
                val credential = didCredential("did:web:issuer-chosen.example", subject)
                batchTestClient(credential = { credentialResponse(credential) }).use { http ->
                    assertFailsWith<IllegalArgumentException> {
                        WalletIssuanceHandler.fetchCredentials(FetchCredentialRequest(
                            Url("$BATCH_TEST_ISSUER/credential"), "access", "identity", proofJwt = proof), http)
                    }
                }
            }
            assertEquals(0, resolutions)
        } finally {
            if (previous == null) DidService.resolverMethods.remove("web")
            else DidService.registerResolverForMethod("web", previous)
        }
    }

    @Test fun aSubjectDidAlreadySelectedByTheProofRemainsUsable() = runTest {
        DidService.minimalInit()
        val fixture = batchTestFixture(true)
        val holder = fixture.keys.first().crypto2Key!!
        val did = DidService.registerByKey("jwk", holder).did
        val proof = CompactJws.sign("{}".encodeToByteArray(), holder, JwsAlgorithm.ES256,
            buildJsonObject { put("typ", "openid4vci-proof+jwt"); put("kid", "$did#0") })
        val credential = didCredential(did, subject = true)
        batchTestClient(credential = { credentialResponse(credential) }).use { http ->
            val result = WalletIssuanceHandler.fetchCredentials(FetchCredentialRequest(
                Url("$BATCH_TEST_ISSUER/credential"), "access", "identity", proofJwt = proof), http)
            assertEquals(listOf(credential), result.rawCredentials)
        }
    }

    private suspend fun didCredential(did: String, subject: Boolean): String {
        val issuer = batchTestLegacyKey()
        val jwt = issuer.signJws(buildJsonObject {
            put("iss", BATCH_TEST_ISSUER)
            if (subject) {
                put("sub", did)
                putJsonObject("vc") {
                    putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                    putJsonArray("type") { add("VerifiableCredential") }
                    putJsonObject("credentialSubject") { put("id", did) }
                }
            } else {
                put("vct", "identity")
                putJsonObject("cnf") { put("kid", did) }
            }
        }.toString().encodeToByteArray(), mapOf("typ" to JsonPrimitive(if (subject) "JWT" else "dc+sd-jwt")))
        return if (subject) jwt else "$jwt~"
    }

    private fun credentialResponse(credential: String) = buildJsonObject {
        putJsonArray("credentials") { add(buildJsonObject { put("credential", credential) }) }
    }.toString()
}
