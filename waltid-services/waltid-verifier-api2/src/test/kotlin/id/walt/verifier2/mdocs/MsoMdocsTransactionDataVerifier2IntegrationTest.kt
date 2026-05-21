@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.verifier2.mdocs

import id.walt.cose.*
import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.representations.X5CCertificateString
import id.walt.credentials.representations.X5CList
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.digest.ValueDigest
import id.walt.mdoc.objects.digest.ValueDigestList
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.mso.DeviceKeyInfo
import id.walt.mdoc.objects.mso.KeyAuthorization
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.objects.mso.ValidityInfo
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.transactiondata.profile.TransactionDataTypeProfileRegistry
import id.walt.verifier2.DOC_SIGNING_TYPE
import id.walt.verifier2.DocumentSigningProfile
import id.walt.verifier2.OSSVerifier2FeatureCatalog
import id.walt.verifier2.PAYMENT_TYPE
import id.walt.verifier2.PaymentAuthorizationProfile
import id.walt.verifier2.OSSVerifier2ServiceConfig
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.OpenId4VPConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import id.walt.verifier2.verifierModule
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class MsoMdocsTransactionDataVerifier2IntegrationTest {

    private val profileRegistry = TransactionDataTypeProfileRegistry(
        PaymentAuthorizationProfile,
        DocumentSigningProfile,
    )

    private val issuerKey: JWKKey = runBlocking {
        KeyManager.resolveSerializedKey(
            """{"type":"jwk","jwk":{"kty":"EC","d":"-wSIL_tMH7-mO2NAfHn03I8ZWUHNXVzckTTb96Wsc1s","crv":"P-256","x":"Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U","y":"6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo"}}"""
        ) as JWKKey
    }

    private val holderKey: JWKKey = runBlocking {
        KeyManager.resolveSerializedKey(
            """{"type":"jwk","jwk":{"kty":"EC","d":"QN9Y3k_3Hy2OV0C5Pmez_ObEXJKcXonnMg3xTpcLOAg","crv":"P-256","x":"eTT2WdzlmOWBItdgSmsqB1_BP69wfuwOe1IYvaY1WdI","y":"wbOu3GP02JiOVIRQ_ufWLRNOmDB6seYAabCmsGBfr_4"}}"""
        ) as JWKKey
    }

    private val issuerCertPem = "MIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M"
    private val issuerCertCose = listOf(CoseCertificate(issuerCertPem.decodeFromBase64()))

    private val docType = "org.iso.18013.5.1.mDL"
    private val namespace = "org.iso.18013.5.1"

    private val mdocDcqlQuery = DcqlQuery(
        credentials = listOf(
            CredentialQuery(
                id = "my_mdl",
                format = CredentialFormat.MSO_MDOC,
                meta = MsoMdocMeta(doctypeValue = docType),
                claims = listOf(
                    ClaimsQuery(pathStrings = listOf(namespace, "family_name")),
                    ClaimsQuery(pathStrings = listOf(namespace, "given_name")),
                )
            )
        )
    )

    private fun issueMdoc(authorizedTransactionDataType: String, authorizedElements: List<String>): MdocsCredential = runBlocking {
        val holderCoseKey = holderKey.getPublicKey().getCosePublicKey()

        val namespaceData = buildJsonObject {
            put("family_name", "Doe")
            put("given_name", "John")
            put("birth_date", "1986-03-22")
            put("issue_date", "2019-10-20")
            put("expiry_date", "2024-10-20")
            put("issuing_country", "AT")
            put("issuing_authority", "AT DMV")
            put("document_number", "123456789")
        }

        var idx = 0u
        val issuerSignedItems = namespaceData.mapNotNull { (elementIdentifier, elementValueJson) ->
            MdocIssuer.defaultSchemalessMappingFunction(docType, namespace, elementIdentifier, elementValueJson)
                ?.let { IssuerSignedItem.create(idx++, elementIdentifier, it) }
        }

        val valueDigests = mapOf(
            namespace to ValueDigestList(issuerSignedItems.map { item ->
                ValueDigest.fromIssuerSignedItem(item, namespace, "SHA-256")
            })
        )

        val now = Clock.System.now()
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            docType = docType,
            valueDigests = valueDigests,
            deviceKeyInfo = DeviceKeyInfo(
                deviceKey = holderCoseKey,
                keyAuthorizations = KeyAuthorization(
                    dataElements = mapOf(authorizedTransactionDataType to authorizedElements)
                ),
            ),
            validityInfo = ValidityInfo(
                signed = now,
                validFrom = now,
                validUntil = now + 365.days,
            ),
        )

        val msoBytes = coseCompliantCbor.encodeToByteArray(mso)
        val msoPayload = byteArrayOf(0xd8.toByte(), 24.toByte()) + coseCompliantCbor.encodeToByteArray(ByteArraySerializer(), msoBytes)

        val issuerAuth = CoseSign1.createAndSign(
            protectedHeaders = CoseHeaders(algorithm = issuerKey.keyType.toCoseAlgorithm()),
            unprotectedHeaders = CoseHeaders(x5chain = issuerCertCose),
            payload = msoPayload,
            signer = issuerKey.toCoseSigner()
        )

        val issuerSigned = IssuerSigned.fromIssuerSignedItems(
            namespacedItems = mapOf(namespace to issuerSignedItems),
            issuerAuth = issuerAuth
        )

        val document = Document(
            docType = docType,
            issuerSigned = issuerSigned,
            deviceSigned = DeviceSigned(
                ByteStringWrapper(DeviceNameSpaces(emptyMap())),
                DeviceAuth(deviceMac = CoseMac0(ByteArray(0), CoseHeaders(), ByteArray(0), ByteArray(0)))
            )
        )

        val signed = coseCompliantCbor.encodeToHexString(document)

        MdocsCredential(
            credentialData = buildJsonObject {
                putJsonObject(namespace) {
                    namespaceData.forEach { (k, v) -> put(k, v) }
                }
                put("docType", docType)
            },
            signed = signed,
            docType = docType,
            signature = CoseCredentialSignature(
                x5cList = X5CList(listOf(X5CCertificateString(issuerCertPem))),
                signerKey = DirectSerializedKey(issuerKey.getPublicKey())
            ),
        )
    }

    private fun selectCredentialsForQuery(
        credentials: List<MdocsCredential>,
        query: DcqlQuery,
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
        val dcqlCredentials = credentials.mapIndexed { idx, credential ->
            RawDcqlCredential(
                id = idx.toString(),
                format = credential.format,
                data = credential.credentialData,
                originalCredential = credential,
                disclosures = null,
            )
        }
        val matched = DcqlMatcher.match(query, dcqlCredentials).getOrThrow()
        if (matched.isEmpty()) {
            throw IllegalArgumentException("No matching credential")
        }
        return matched
    }

    private fun runTransactionDataTest(
        port: Int,
        sessionSetup: VerificationSessionSetup,
        walletCredentials: List<MdocsCredential>,
    ) {
        val host = "127.0.0.1"

        E2ETest(host, port, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service", OSSVerifier2ServiceConfig(
                        clientId = "verifier2",
                        clientMetadata = ClientMetadata(
                            clientName = "Verifier2",
                            logoUri = "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/4d493ccf-c893-4882-925f-fda3256c38f4/Walt.id_Logo_transparent.png"
                        ),
                        urlPrefix = "http://$host:$port/verification-session",
                        urlHost = "openid4vp://authorize"
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

            val verificationSessionResponse = testAndReturn("Create verification session") {
                http.post("/verification-session/create") {
                    setBody(sessionSetup)
                }.body<VerificationSessionCreationResponse>()
            }

            val sessionId = verificationSessionResponse.sessionId
            val bootstrapUrl = verificationSessionResponse.bootstrapAuthorizationRequestUrl

            val presentationResult = testAndReturn("Present mdoc with transaction data") {
                WalletPresentFunctionality2.walletPresentHandling(
                    holderKey = holderKey,
                    holderDid = null,
                    presentationRequestUrl = bootstrapUrl!!,
                    selectCredentialsForQuery = { query -> selectCredentialsForQuery(walletCredentials, query) },
                    holderPoliciesToRun = null,
                    runPolicies = null,
                    transactionDataTypeRegistry = profileRegistry,
                )
            }

            test("Wallet presentation succeeds") {
                assertTrue(presentationResult.isSuccess, "Presentation failed: ${presentationResult.exceptionOrNull()}")
                val resp = presentationResult.getOrThrow()
                assertTrue(resp.transmissionSuccess!!, "Transmission failed")
                assertEquals(resp.verifierResponse!!.jsonObject["status"]!!.jsonPrimitive.content, "received")
            }

            val info2Json = testAndReturn("View presented session") {
                http.get("/verification-session/$sessionId/info").body<JsonObject>()
            }
            val info2 = Json.decodeFromJsonElement<Verification2Session>(info2Json)

            test("Verification session succeeds with transaction data policy") {
                assertTrue(info2.attempted)
                assertEquals(info2.status, Verification2Session.VerificationSessionStatus.SUCCESSFUL)
                assertNotNull(info2.presentedCredentials)
                assertNotNull(info2.presentedCredentials!!["my_mdl"])
                assertNotNull(info2.policyResults)
                assertTrue(info2.policyResults!!.overallSuccess)
                assertTrue(
                    info2.policyResults!!.vpPolicies["my_mdl"]?.get("mso_mdoc/transaction-data-hash-check")?.success!!,
                    "mso_mdoc/transaction-data-hash-check policy should pass"
                )
            }
        }
    }

    @Test
    fun `payment authorization - hash binding only`() {
        val credential = issueMdoc(
            authorizedTransactionDataType = PAYMENT_TYPE,
            authorizedElements = listOf("transaction_data_hash", "transaction_data_hash_alg"),
        )
        val sessionSetup = CrossDeviceFlowSetup(
            core = GeneralFlowConfig(dcqlQuery = mdocDcqlQuery),
            openid = OpenId4VPConfig(
                transactionData = listOf(paymentTransactionDataItem(credentialId = "my_mdl", amount = "42.00"))
            )
        )
        runTransactionDataTest(port = 17033, sessionSetup = sessionSetup, walletCredentials = listOf(credential))
    }

    @Test
    fun `document signing - hash binding with extra response items`() {
        val credential = issueMdoc(
            authorizedTransactionDataType = DOC_SIGNING_TYPE,
            authorizedElements = listOf("transaction_data_hash", "transaction_data_hash_alg", "document_reference"),
        )
        val sessionSetup = CrossDeviceFlowSetup(
            core = GeneralFlowConfig(dcqlQuery = mdocDcqlQuery),
            openid = OpenId4VPConfig(
                transactionData = listOf(docSigningTransactionDataItem(credentialId = "my_mdl"))
            )
        )
        runTransactionDataTest(port = 17034, sessionSetup = sessionSetup, walletCredentials = listOf(credential))
    }

    private fun paymentTransactionDataItem(credentialId: String, amount: String): String {
        val json = buildJsonObject {
            put("type", JsonPrimitive(PAYMENT_TYPE))
            put("credential_ids", JsonArray(listOf(JsonPrimitive(credentialId))))
            put("transaction_data_hashes_alg", JsonArray(listOf(JsonPrimitive("sha-256"))))
            put("amount", JsonPrimitive(amount))
            put("currency", JsonPrimitive("EUR"))
            put("payee", JsonPrimitive("ACME Corp"))
        }.toString()
        return json.toByteArray().encodeToBase64Url()
    }

    private fun docSigningTransactionDataItem(credentialId: String): String {
        val json = buildJsonObject {
            put("type", JsonPrimitive(DOC_SIGNING_TYPE))
            put("credential_ids", JsonArray(listOf(JsonPrimitive(credentialId))))
            put("transaction_data_hashes_alg", JsonArray(listOf(JsonPrimitive("sha-256"))))
            put("document_hash", JsonPrimitive("abc123def456"))
            put("hash_algorithm_identifier", JsonPrimitive("sha-256"))
            put("document_reference", JsonPrimitive("contract-2024-001"))
        }.toString()
        return json.toByteArray().encodeToBase64Url()
    }
}
