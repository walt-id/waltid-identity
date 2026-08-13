package id.walt.verifier2.mdocs

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.representations.X5CCertificateString
import id.walt.credentials.representations.X5CList
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeyManager
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.data.DcApiAnnexDFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.verifierModule
import id.waltid.openid4vp.wallet.DcApiWallet
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * DC API (OpenID4VP 1.0 Appendix A) mdoc round trip against the real verifier.
 *
 * The real verifier consumes the wallet's response and runs its actual mdoc policies (device auth,
 * issuer auth), which no test driving only the Credential Manager plumbing can check: a wrong session
 * transcript or a re-encoded issuer signature is invisible without verification.
 */
class DcApiMdocVerifier2IntegrationTest {

    private val origin = "https://verifier.example.com"

    private val mdocsDcqlQuery = DcqlQuery(
        credentials = listOf(
            CredentialQuery(
                id = "my_mdl",
                format = CredentialFormat.MSO_MDOC,
                meta = MsoMdocMeta(doctypeValue = "org.iso.18013.5.1.mDL"),
                claims = listOf(
                    ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "family_name")),
                    ClaimsQuery(pathStrings = listOf("org.iso.18013.5.1", "given_name")),
                ),
            )
        )
    )

    private val policies = Verification2Session.DefinedVerificationPolicies(
        vc_policies = VCPolicyList(listOf(CredentialSignaturePolicy()))
    )

    /** Unsigned request, unencrypted response. Signed+encrypted coverage is in DcApiWalletTest. */
    private val verificationSessionSetup: VerificationSessionSetup = DcApiAnnexDFlowSetup(
        core = GeneralFlowConfig(
            dcqlQuery = mdocsDcqlQuery,
            policies = policies,
            signedRequest = false,
            encryptedResponse = false,
        ),
        expectedOrigins = listOf(origin),
    )

    /**
     * The same issuer-signed mDL fixture the cross-device mdoc integration test uses, so a failure
     * here is attributable to the DC API path rather than to the credential.
     */
    private val walletCredentials = listOf(
        MdocsCredential(
            credentialData = Json.decodeFromString(
                """
                {
                    "org.iso.18013.5.1": {
                        "family_name": "Doe",
                        "given_name": "John",
                        "birth_date": "1986-03-22",
                        "issue_date": "2019-10-20",
                        "expiry_date": "2024-10-20",
                        "issuing_country": "AT",
                        "issuing_authority": "AT DMV",
                        "document_number": 123456789,
                        "un_distinguishing_sign": "AT"
                    },
                    "docType": "org.iso.18013.5.1.mDL"
                }
                """
            ),
            signed = MDL_FIXTURE_SIGNED_HEX,
            docType = "org.iso.18013.5.1.mDL",
            signature = CoseCredentialSignature(
                x5cList = X5CList(listOf(X5CCertificateString(MDL_FIXTURE_X5C))),
                signerKey = DirectSerializedKey(
                    runBlocking { KeyManager.resolveSerializedKey(MDL_FIXTURE_SIGNER_JWK) }
                ),
            ),
        )
    )

    /**
     * The holder key must be the `deviceKey` inside the fixture's MSO, otherwise `device_key_auth`
     * fails for reasons unrelated to the DC API transcript.
     */
    private val holderCrypto2KeyFun = suspend {
        CryptoRuntime(defaultSoftwareKeyProviders()).restore(
            EncodedKey.Jwk(
                BinaryData(HOLDER_JWK.encodeToByteArray()),
                privateMaterial = true,
            ).toStoredSoftwareKey(
                id = KeyId("dc-api-test-holder"),
                usages = setOf(KeyUsage.SIGN),
            )
        )
    }

    /**
     * Walks the `policy_results` tree and collects every `success == false` leaf, with its
     * `errors`, so a failure names the policy instead of just reporting a false boolean.
     */
    private fun JsonObject.failedPolicies(): List<String> = buildList {
        fun walk(element: JsonElement, path: String) {
            when (element) {
                is JsonObject -> {
                    val id = element["policy_executed"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                    val success = element["success"]?.jsonPrimitive?.booleanOrNull
                    if (id != null && success == false) {
                        add("$path/$id: ${element["errors"]}")
                    }
                    element.forEach { (key, value) -> walk(value, "$path/$key") }
                }

                is JsonArray -> element.forEachIndexed { idx, value -> walk(value, "$path[$idx]") }
                else -> Unit
            }
        }
        walk(this@failedPolicies, "")
    }

    private fun JsonObject.executedPolicyIds(): Set<String> = buildSet {
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    element["policy_executed"]?.jsonObject?.get("id")?.jsonPrimitive?.content
                        ?.let { add(it) }
                    element.values.forEach(::walk)
                }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(this@executedPolicyIds)
    }

    private suspend fun selectCredentialsForQuery(
        query: DcqlQuery,
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
        val dcqlCredentials = walletCredentials.mapIndexed { idx, credential ->
            RawDcqlCredential(
                id = idx.toString(),
                format = credential.format,
                data = credential.credentialData,
                originalCredential = credential,
                disclosures = null,
            )
        }
        val matched = DcqlMatcher.match(query, dcqlCredentials).getOrThrow()
        if (matched.isEmpty()) throw IllegalArgumentException("No matching credential")
        return matched
    }

    @Test
    fun test() = runRoundTrip(port = 17021, walletOrigin = origin, expectSuccess = true)

    /**
     * The wallet asserts an origin the verifier was not configured with, so the two sides hash different
     * [OpenID4VPDCAPIHandoverInfo] and only `mso_mdoc/device-auth` fails. Issuer auth does not involve
     * the origin, so "device auth and issuer auth both failed" is a different diagnosis.
     */
    @Test
    fun mismatchedOriginFailsOnlyDeviceAuth() =
        runRoundTrip(port = 17022, walletOrigin = "https://attacker.example.com", expectSuccess = false)

    private fun runRoundTrip(port: Int, walletOrigin: String, expectSuccess: Boolean) {
        val host = "127.0.0.1"

        E2ETest(host, port, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service", OSSVerifier2ServiceConfig(
                        clientId = "verifier2",
                        clientMetadata = ClientMetadata(clientName = "Verifier2"),
                        urlPrefix = "http://$host:$port/verification-session",
                        urlHost = "openid4vp://authorize",
                    )
                )
            },
            init = {
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
            },
            module = Application::verifierModule
        ) {
            val http = testHttpClient()

            val sessionResponse = testAndReturn("Create DC API verification session") {
                val raw = http.post("/verification-session/create") {
                    setBody(verificationSessionSetup)
                }
                check(raw.status.isSuccess()) {
                    "Session creation failed: HTTP ${raw.status}: ${raw.bodyAsText()}"
                }
                raw.body<VerificationSessionCreationResponse>()
            }
            val sessionId = sessionResponse.sessionId

            // The wallet does not fetch a URL in DC API: the browser hands it the request object.
            // This is the same JSON the verifier would give navigator.credentials.get().
            val requestEnvelope = testAndReturn("Fetch DC API request object") {
                http.get("/verification-session/$sessionId/request").body<JsonObject>()
            }
            println("DC API request envelope: $requestEnvelope")

            val protocolRequest = requestEnvelope["digital"]!!.jsonObject["requests"]!!
                .jsonArray.single().jsonObject
            val protocol = protocolRequest["protocol"]!!.jsonPrimitive.content
            val requestData = protocolRequest["data"]!!.jsonObject

            test("Verifier offers the unsigned DC API protocol") {
                assertEquals("openid4vp-v1-unsigned", protocol)
            }

            // Wallet side: resolve exactly as the platform adapter does, with an OS-asserted origin.
            val resolved = testAndReturn("Wallet resolves DC API request") {
                DcApiWallet.resolveRequest(
                    protocol = protocol,
                    data = requestData,
                    origin = walletOrigin,
                )
            }

            val dcApiResponse = testAndReturn("Wallet builds DC API response") {
                WalletPresentFunctionality2.walletPresentDcApiHandling(
                    holderKey = holderCrypto2KeyFun(),
                    holderDid = null,
                    request = resolved,
                    selectCredentialsForQuery = ::selectCredentialsForQuery,
                    transactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
                ).getOrThrow()
            }
            println("Wallet DC API response: ${DcApiWallet.encodeResponse(dcApiResponse)}")

            // The browser posts this back to the verifier. Real verification happens here.
            val verifierResponse = testAndReturn("Submit DC API response to verifier") {
                http.post("/verification-session/$sessionId/response") {
                    contentType(ContentType.Application.Json)
                    setBody(DcApiWallet.encodeResponse(dcApiResponse))
                }
            }
            val verifierBody = verifierResponse.bodyAsText()
            println("Verifier HTTP ${verifierResponse.status}: $verifierBody")

            if (expectSuccess) {
                test("Verifier accepted the DC API presentation") {
                    assertTrue(
                        "Verifier rejected the presentation: HTTP ${verifierResponse.status} $verifierBody"
                    ) { verifierResponse.status.isSuccess() }
                }
            }

            // Parsed as JSON rather than as Verification2Session: `PresentedRawData.state` has no default,
            // and a DC API session has no `state`, so the omitted field breaks the typed decode.
            val info = testAndReturn("View presented session") {
                http.get("/verification-session/$sessionId/info").body<JsonObject>()
            }
            println("Session info: $info")

            // A rejected presentation never reaches `policy_results`; its per-policy outcomes are
            // reported under `presentation_validation_results` instead.
            val policyResults = assertNotNull(
                info["policy_results"] ?: info["presentation_validation_results"],
                "Neither policy_results nor presentation_validation_results present: $info",
            ).jsonObject
            val failed = policyResults.failedPolicies()

            if (expectSuccess) {
                test("Verification succeeded with passing policies") {
                    assertTrue { info["attempted"]!!.jsonPrimitive.boolean }
                    assertEquals("SUCCESSFUL", info["status"]!!.jsonPrimitive.content)
                    assertNotNull(info["presented_credentials"]!!.jsonObject["my_mdl"])
                    assertTrue("Failed policies: $failed") { failed.isEmpty() }
                    // Device auth is the DC API session transcript check: both sides must have
                    // hashed the same origin. Assert explicitly so it cannot silently disappear.
                    assertTrue("device-auth policy did not run: $policyResults") {
                        policyResults.executedPolicyIds().contains("mso_mdoc/device-auth")
                    }
                }
            } else {
                test("Only device auth fails when the origins diverge") {
                    assertEquals("FAILED", info["status"]!!.jsonPrimitive.content)
                    assertTrue("Expected exactly one failed policy, got: $failed") {
                        failed.size == 1
                    }
                    assertTrue("Expected mso_mdoc/device-auth to fail, got: $failed") {
                        failed.single().contains("mso_mdoc/device-auth")
                    }
                    assertTrue("Issuer auth must be unaffected by the origin, got: $failed") {
                        failed.none { it.contains("issuer_auth") }
                    }
                }
            }
        }
    }
}
