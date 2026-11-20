@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.transactiondata.Ts12Payment
import id.walt.verifier.openid.transactiondata.Ts12PaymentValidationException
import id.waltid.openid4vp.wallet.presentation.ScaKeyBindingContext
import id.waltid.openid4vp.wallet.presentation.ScaPresentationAuthorizer
import id.waltid.openid4vp.wallet.presentation.ScaAuthenticationMethods
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.io.encoding.Base64
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.cose.*
import id.walt.credentials.formats.W3C11
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.DidService
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.mdoc.objects.MdocsCborSerializer
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.waltid.openid4vp.wallet.presentation.MdocPresenter
import id.waltid.openid4vp.wallet.presentation.PresentationClaimsProjection
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.json.*
import kotlin.test.*

class WalletCrypto2PresentationSigningTest {
    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun mdocReviewUsesSignedPathsWhenDisplaySerializerChangesTheShape() {
        val namespace = "example.mdoc.review"
        val field = "evidence"
        val byteString = CborByteString(byteArrayOf(1, 2, 3))
        val signedValue = CborMap(mapOf(
            CborString("raw") to byteString,
            CborString("items") to CborArray(listOf(CborByteString(byteArrayOf(4, 5, 6)), CborString("caption"))),
        ))
        val document = Document("example.mdoc", IssuerSigned.fromIssuerSignedItems(
            mapOf(namespace to listOf(IssuerSignedItem.create(0u, field, signedValue))),
            CoseSign1(byteArrayOf(), CoseHeaders(), null, byteArrayOf()),
        ))
        val display = Json.parseToJsonElement("""{"attachment":[1,2,3],"items":["caption",[4,5,6]]}""")
        val serializer = object : KSerializer<CborMap> {
            override val descriptor = buildClassSerialDescriptor("DisplayEvidence")
            override fun deserialize(decoder: Decoder): CborMap = error("Display only")
            override fun serialize(encoder: Encoder, value: CborMap) {
                assertEquals(signedValue, value)
                (encoder as JsonEncoder).encodeJsonElement(display)
            }
        }
        MdocsCborSerializer.register(mapOf(field to serializer), namespace)
        val stored = document.issuerSigned.namespacesToJson()
        assertEquals(display, stored.getValue(namespace).jsonObject[field])
        val credential = MdocsCredential(
            JsonObject(stored + ("docType" to JsonPrimitive(document.docType))),
            coseCompliantCbor.encodeToByteArray(document).encodeToBase64Url(),
            document.docType,
        )
        val raw = RawDcqlCredential("one", credential.format, credential.credentialData, originalCredential = credential)
        val query = CredentialQuery("q", CredentialFormat.MSO_MDOC, meta = MsoMdocMeta(document.docType),
            claims = listOf(ClaimsQuery(pathStrings = listOf(namespace, field))))
        val review = PresentationClaimsProjection.prepare(credential, DcqlMatcher.matchCredential(query, raw).getOrThrow())
        assertEquals(
            Json.parseToJsonElement("""{"$namespace":{"evidence":{"raw":"AQID","items":["BAUG","caption"]}}}"""),
            review.claims,
        )
        val path = listOf(JsonPrimitive(namespace), JsonPrimitive(field))
        assertEquals(
            setOf(path + JsonPrimitive("raw"), path + JsonPrimitive("items") + JsonPrimitive(0)),
            review.locations.filter { it.isBinary }.map { it.receivedPath }.toSet(),
        )
        val caption = path + JsonPrimitive("items") + JsonPrimitive(1)
        assertEquals(caption, review.locations.single { it.receivedPath == caption }.sourcePath)
        assertEquals(7, review.locations.size)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun mdocReviewKeepsBinaryValuesCompactWithoutCollapsingNumericArrays() {
        val namespace = "org.iso.18013.5.1"
        for (size in listOf(0, 32, 256 * 1024)) {
            val bytes = ByteArray(size) { (it * 73).toByte() }
            val nested = CborMap(mapOf(
                CborString("image") to CborByteString(bytes),
                CborInteger(0) to CborByteString(byteArrayOf(1, 2, 3)),
                CborString("text") to CborString("AQID"),
                CborString("images") to CborArray(listOf(CborByteString(byteArrayOf(4, 5, 6)))),
                CborString("numbers") to CborArray(listOf(CborInteger(1), CborInteger(0), CborInteger(-1))),
            ))
            val document = Document("org.iso.18013.5.1.mDL", IssuerSigned.fromIssuerSignedItems(
                mapOf(namespace to listOf(
                    IssuerSignedItem.create(0u, "portrait", CborByteString(bytes)),
                    IssuerSignedItem.create(1u, "nested", nested),
                    IssuerSignedItem.create(2u, "unrequested", CborString("not shared")),
                )), CoseSign1(byteArrayOf(), CoseHeaders(), null, byteArrayOf()),
            ))
            val signed = coseCompliantCbor.encodeToByteArray(document)
            val credential = MdocsCredential(document.issuerSigned.namespacesToJson(),
                signed.encodeToBase64Url(), document.docType)
            val requested = listOf("portrait", "nested").map { ClaimsQuery(pathStrings = listOf(namespace, it)) }
            val query = CredentialQuery("mdl", CredentialFormat.MSO_MDOC, meta = MsoMdocMeta(document.docType), claims = requested)
            val match = DcqlMatcher.DcqlMatchResult(
                RawDcqlCredential("mdl", credential.format, credential.credentialData, originalCredential = credential),
                requested.associate { claim -> claim.path.joinToString(".") to
                    credential.credentialData.getValue(namespace).jsonObject.getValue(claim.path.last().jsonPrimitive.content) }, query,
            )
            val projection = PresentationClaimsProjection.prepare(credential, match)
            val claims = projection.claims.getValue(namespace).jsonObject
            assertContentEquals(bytes, claims.getValue("portrait").jsonPrimitive.content.decodeFromBase64Url())
            val nestedClaims = claims.getValue("nested").jsonObject
            assertContentEquals(bytes, nestedClaims.getValue("image").jsonPrimitive.content.decodeFromBase64Url())
            assertContentEquals(byteArrayOf(1, 2, 3), nestedClaims.getValue("0").jsonPrimitive.content.decodeFromBase64Url())
            assertEquals(JsonPrimitive("AQID"), nestedClaims["text"])
            assertContentEquals(byteArrayOf(4, 5, 6), nestedClaims.getValue("images").jsonArray.single().jsonPrimitive.content.decodeFromBase64Url())
            assertEquals(Json.parseToJsonElement("[1,0,-1]"), nestedClaims["numbers"])
            assertEquals(setOf("portrait", "nested"), claims.keys)
            val expected = listOf(
                emptyList(), listOf(namespace), listOf(namespace, "portrait"), listOf(namespace, "nested"),
                listOf(namespace, "nested", "image"), listOf(namespace, "nested", "0"),
                listOf(namespace, "nested", "images"), listOf(namespace, "nested", "text"),
                listOf(namespace, "nested", "numbers"),
            ).map { path -> path.map(::JsonPrimitive) } + (0..2).map { index ->
                listOf(JsonPrimitive(namespace), JsonPrimitive("nested"), JsonPrimitive("numbers"), JsonPrimitive(index))
            } + listOf(listOf(JsonPrimitive(namespace), JsonPrimitive("nested"), JsonPrimitive("images"), JsonPrimitive(0)))
            assertEquals(expected.toSet(), projection.locations.map { it.sourcePath }.toSet())
            assertEquals(13, projection.locations.size)
            assertEquals(
                setOf(
                    listOf(namespace, "portrait").map(::JsonPrimitive),
                    listOf(namespace, "nested", "image").map(::JsonPrimitive),
                    listOf(namespace, "nested", "0").map(::JsonPrimitive),
                    listOf(JsonPrimitive(namespace), JsonPrimitive("nested"), JsonPrimitive("images"), JsonPrimitive(0)),
                ),
                projection.locations.filter { it.isBinary }.map { it.receivedPath }.toSet(),
            )
            projection.locations.forEach { location ->
                assertEquals(location.sourcePath, location.receivedPath)
                assertFalse(location.alwaysCarried)
                val selected = when (location.sourcePath.getOrNull(1)) {
                    JsonPrimitive("portrait") -> listOf("\"$namespace\".\"portrait\"")
                    JsonPrimitive("nested") -> listOf("\"$namespace\".\"nested\"")
                    else -> emptyList()
                }
                assertEquals(selected, location.sharedBy)
            }
            assertEquals(document, credential.document)
            assertIs<JsonArray>(credential.document.issuerSigned.namespacesToJson().getValue(namespace).jsonObject.getValue("portrait"))
        }
    }

    @Test
    fun wholeCredentialProjectionUsesTheSignedJwtIncludingUnrequestedClaims() {
        val payload = Json.parseToJsonElement("""{"iss":"https://issuer.example","vc":{"credentialSubject":{
            "name":"Ada","address":{"city":"London","country":"GB"}}}}""").jsonObject
        fun encoded(value: JsonObject) = value.toString().encodeToByteArray().encodeToBase64Url()
        val credential = W3C11(credentialData = buildJsonObject { put("untrusted-stored-only", "not sent") },
            signed = "${encoded(buildJsonObject { put("alg", "ES256") })}.${encoded(payload)}.c2ln",
            signature = JwtCredentialSignature("signature", buildJsonObject {}))
        val match = DcqlMatcher.DcqlMatchResult(
            RawDcqlCredential("credential", "jwt_vc_json", credential.credentialData, originalCredential = credential),
            selectedDisclosures = null,
            originalQuery = CredentialQuery("vc", CredentialFormat.JWT_VC_JSON, meta = NoMeta),
        )
        val projection = PresentationClaimsProjection.prepare(credential, match)
        assertTrue(projection.wholeCredential)
        assertEquals(payload, projection.claims)
        val city = projection.locations.single {
            it.receivedPath == listOf("vc", "credentialSubject", "address", "city").map(::JsonPrimitive)
        }
        assertEquals(listOf("credentialSubject", "address", "city").map(::JsonPrimitive), city.sourcePath)
    }

    @Test fun scheduledPaymentCannotExpireWhileAnotherCredentialIsResolved() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val key = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(KeyId("review-holder"),
            KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)))
        val public = requireNotNull(key.capabilities.publicKeyExporter).exportPublicKey().toPublicJwk(key.spec)
        val claims = buildJsonObject {
            put("_sd_alg", "sha-256"); put("vct", "https://issuer.example/payment")
            put("cnf", buildJsonObject { put("jwk", Jwk.parse(public)) })
        }
        val signed = CompactJws.sign(claims.toString().encodeToByteArray(), key, JwsAlgorithm.ES256)
        val credential = SdJwtCredential(CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
            credentialData = claims, signature = null, signed = signed)
        val matches = listOf("payment", "second").associateWith { id ->
            val query = CredentialQuery(id, CredentialFormat.DC_SD_JWT, meta = NoMeta)
            listOf(DcqlMatcher.DcqlMatchResult(RawDcqlCredential(id, credential.format, claims,
                originalCredential = credential), null, query))
        }
        val deadline = Instant.parse("2099-01-01T00:00:00Z")
        val clock = object : Clock {
            var current = deadline
            override fun now() = current
        }
        val payload = Json.parseToJsonElement("""{"transaction_id":"review","payee":{"name":"Shop","id":"merchant"},"currency":"EUR","amount":1,"execution_date":"$deadline"}""").jsonObject
        val entry = buildJsonObject {
            put("type", Ts12Payment.TYPE); put("credential_ids", buildJsonArray { add("payment") }); put("payload", payload)
        }.toString().encodeToByteArray().encodeToBase64Url()
        val request = AuthorizationRequest(clientId="verifier", nonce="nonce", responseMode=OpenID4VPResponseMode.DC_API,
            transactionData=listOf(entry))
        val failure = assertFailsWith<Ts12PaymentValidationException> {
            WalletPresentFunctionality2.generateVpTokenForRequest(request, matches, null, null,
            TransactionDataTypeRegistry(), null, key,
            scaAuthorizer = ScaPresentationAuthorizer {
                ScaAuthenticationMethods.PossessionAndInherence(ScaAuthenticationMethods.Possession.OTHER, ScaAuthenticationMethods.Inherence.OTHER)
            }, credentialHolderKeyResolver = CredentialPresentationKeyResolver { id, _ ->
                if (id == "second") clock.current = deadline + 1.seconds
                null
            }, clock = clock)
        }
        assertEquals("/execution_date", failure.path)
        assertEquals(listOf(entry), request.transactionData)
    }


    @Test
    fun paymentDeadlineIsCheckedAfterResponseEncryptionBeforeHandoff() = runTest {
        val deadline = Instant.parse("2099-01-01T00:00:00Z")
        val entry = """{"type":"urn:eudi:sca:payment:1","credential_ids":["payment"],"payload":{"transaction_id":"final-response","payee":{"name":"Shop","id":"merchant"},"currency":"EUR","amount":1,"execution_date":"$deadline"}}"""
            .encodeToByteArray().encodeToBase64Url()
        val encryptionKey = Json.parseToJsonElement("""{
            "kty":"EC","crv":"P-256","x":"y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
            "y":"jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg","use":"enc","kid":"enc-key","alg":"ECDH-ES"
        }""").jsonObject
        for (mode in listOf(OpenID4VPResponseMode.DC_API, OpenID4VPResponseMode.DC_API_JWT, OpenID4VPResponseMode.DIRECT_POST_JWT)) {
            var reads = 0
            val clock = object : Clock {
                override fun now(): Instant = if (reads++ == 0) deadline else deadline + 1.seconds
            }
            val request = AuthorizationRequest(clientId = "verifier", nonce = "nonce", responseMode = mode,
                responseUri = "https://verifier.example/response", transactionData = listOf(entry),
                clientMetadata = ClientMetadata(jwks = ClientMetadata.Jwks(listOf(encryptionKey)),
                    encryptedResponseEncValuesSupported = listOf("A256GCM")))
            val error = if (mode == OpenID4VPResponseMode.DIRECT_POST_JWT) {
                assertIs<Ts12PaymentValidationException>(WalletPresentFunctionality2.sendAuthorizationResponse(
                    request, "{}", null, request.clientMetadata, clock).exceptionOrNull())
            } else assertFailsWith<Ts12PaymentValidationException> {
                DcApiWallet.buildResponse(ResolvedDcApiRequest(DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED,
                    "https://verifier.example", request), "{}", null, clock)
            }
            assertEquals("/execution_date", error.path)
            assertEquals(2, reads)
            assertEquals(listOf(entry), request.transactionData)
        }
    }

    @Test
    fun scheduledPaymentExpiringDuringNativeSigningNeverReleasesAProof() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val original = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId("scheduled-holder"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)))
        val deadline = Instant.parse("2026-11-04T12:00:00Z")
        val clock = object : Clock {
            var current = deadline
            override fun now() = current
        }
        var signatures = 0
        var expireDuringAuthorization = false
        val key = object : Crypto2Key by original {
            override val capabilities = original.capabilities.copy(signer = Signer { data, algorithm ->
                val signature = requireNotNull(original.capabilities.signer).sign(data, algorithm)
                signatures++
                clock.current = Instant.parse("2026-11-04T12:00:01Z")
                signature
            })
        }
        val public = requireNotNull(key.capabilities.publicKeyExporter).exportPublicKey().toPublicJwk(key.spec)
        val issuer = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId("scheduled-issuer"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)))
        val issuerJwt = CompactJws.sign(buildJsonObject {
            put("_sd_alg", "sha-256"); put("cnf", buildJsonObject { put("jwk", Jwk.parse(public)) })
        }.toString().encodeToByteArray(), issuer, JwsAlgorithm.ES256)
        val entry = buildJsonObject {
            put("type", "urn:eudi:sca:payment:1"); put("credential_ids", buildJsonArray { add("payment") })
            put("payload", buildJsonObject {
                put("transaction_id", "scheduled"); put("currency", "EUR"); put("amount", 1.23)
                put("payee", buildJsonObject { put("name", "Shop"); put("id", "merchant") })
                put("execution_date", "2026-11-04T12:00:00Z")
            })
        }.toString().encodeToByteArray()
        val context = ScaKeyBindingContext(
            "payment", OpenID4VPResponseMode.DC_API,
            ScaPresentationAuthorizer {
                if (expireDuringAuthorization) clock.current = Instant.parse("2026-11-04T12:00:01Z")
                ScaAuthenticationMethods.PossessionAndInherence(
                    ScaAuthenticationMethods.Possession.OTHER,
                    ScaAuthenticationMethods.Inherence.OTHER)
            }, clock)
        suspend fun attempt() = WalletPresentFunctionality2.createKeyBindingJwt(
            "$issuerJwt~", "nonce", "verifier", emptyList(), key,
            listOf(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(entry)),
            scaContext = context)
        assertEquals("/execution_date", assertFailsWith<Ts12PaymentValidationException> { attempt() }.path)
        assertEquals(1, signatures)
        assertFailsWith<Ts12PaymentValidationException> { attempt() }
        assertEquals(1, signatures) // Expired submission is rejected before calling the signer.
        clock.current = deadline
        expireDuringAuthorization = true
        assertFailsWith<Ts12PaymentValidationException> { attempt() }
        assertEquals(1, signatures) // A separate authorization callback can expire before signing.
    }

    @Test
    fun `persisted crypto2 key signs when legacy platform adapter cannot`() = runTest {
        val issuerKey = JWKKey.generate(KeyType.Ed25519)
        val crypto2Key = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("platform-key"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val issuerJwt = issuerKey.signJws(
            buildJsonObject { put("_sd_alg", "sha-256") }.toString().encodeToByteArray()
        )

        val token = WalletPresentFunctionality2.createKeyBindingJwt(
            disclosed = "$issuerJwt~",
            nonce = "nonce",
            audience = "verifier",
            selectedDisclosures = emptyList(),
            holderKey = crypto2Key,
        )

        assertEquals(
            "nonce",
            Json.parseToJsonElement(
                CompactJws.verify(token, crypto2Key, JwsAlgorithm.ED25519).payload.decodeToString()
            ).jsonObject["nonce"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `SD-JWT key binding signs through crypto2`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.Ed25519)
        val signingKey = assertNotNull(WalletCrypto2KeyAdapter.signingKey(legacyKey))
        val issuerJwt = legacyKey.signJws(
            buildJsonObject { put("_sd_alg", "sha-256") }.toString().encodeToByteArray()
        )
        val token = WalletPresentFunctionality2.createKeyBindingJwt(
            disclosed = "$issuerJwt~",
            nonce = "nonce",
            audience = "verifier",
            selectedDisclosures = emptyList(),
            holderKey = legacyKey,
        )
        val publicJwk = signingKey.capabilities.publicKeyExporter?.exportPublicKey() as EncodedKey.Jwk
        val storedVerificationKey = V1KeyMigration().migrate(
            recordId = KeyId("kb-jwt-verification"),
            serialized = buildJsonObject {
                put("type", "jwk")
                put("jwk", Jwk.parse(publicJwk))
            },
            usages = setOf(KeyUsage.VERIFY),
        )
        val verificationKey = CryptoRuntime(defaultSoftwareKeyProviders()).restore(storedVerificationKey)
        val verified = CompactJws.verify(token, verificationKey, JwsAlgorithm.ED25519)
        val payload = Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject

        assertEquals("kb+jwt", verified.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertEquals("nonce", payload["nonce"]?.jsonPrimitive?.content)
        assertEquals("verifier", payload["aud"]?.jsonPrimitive?.content)
        assertNotNull(payload["sd_hash"])
    }

    @Test
    fun `direct post response encrypts with strict crypto2 JWE`() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val recipientKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("response-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        val publicJwk = recipientKey.capabilities.publicKeyExporter?.exportPublicKey() as EncodedKey.Jwk
        val verifierJwk = JsonObject(
            Jwk.parse(publicJwk) + mapOf(
                "kid" to JsonPrimitive(recipientKey.id.value),
                "use" to JsonPrimitive("enc"),
                "alg" to JsonPrimitive("ECDH-ES"),
            )
        )
        val payload = buildJsonObject { put("state", "state") }
        val recipientPublicKey = EncodedKey.Jwk(
            BinaryData(verifierJwk.toString().encodeToByteArray()),
            privateMaterial = false,
        )
        val encrypted = WalletPresentFunctionality2.encryptDirectPostResponse(
            payload,
            recipientPublicKey,
            JweContentEncryption.A128GCM,
        )
        val decrypted = CompactJwe.decrypt(
            encrypted,
            recipientKey,
            setOf(JweContentEncryption.A128GCM),
        )

        assertEquals(recipientKey.id.value, decrypted.protectedHeader["kid"]?.jsonPrimitive?.content)
        assertEquals(payload, Json.parseToJsonElement(decrypted.plaintext.decodeToString()))
        assertFailsWith<IllegalArgumentException> {
            WalletPresentFunctionality2.encryptDirectPostResponse(
                payload,
                EncodedKey.Jwk(
                    BinaryData(JsonObject(verifierJwk - "kid").toString().encodeToByteArray()),
                    privateMaterial = false,
                ),
                JweContentEncryption.A128GCM,
            )
        }
    }

    @Test
    fun `direct post response rejects private material even when mislabeled public`() = runTest {
        val privateJwk = buildJsonObject {
            put("kty", "EC")
            put("crv", "P-256")
            put("kid", "response-key")
            put("alg", "ECDH-ES")
            put("x", "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30")
            put("y", "jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg")
            put("d", "AQ")
        }

        listOf(true, false).forEach { privateMaterial ->
            assertFailsWith<IllegalArgumentException> {
                WalletPresentFunctionality2.encryptDirectPostResponse(
                    payload = buildJsonObject { put("state", "state") },
                    recipientPublicKey = EncodedKey.Jwk(
                        BinaryData(privateJwk.toString().encodeToByteArray()),
                        privateMaterial = privateMaterial,
                    ),
                    contentEncryption = JweContentEncryption.A128GCM,
                )
            }
        }
    }

    @Test
    fun `direct post response selects supported content encryption and ECDH key operations`() {
        assertEquals(
            JweContentEncryption.A256GCM,
            WalletPresentFunctionality2.selectDirectPostContentEncryption(listOf("A192GCM", "A256GCM")),
        )
        assertFailsWith<IllegalArgumentException> {
            WalletPresentFunctionality2.selectDirectPostContentEncryption(listOf("A192GCM"))
        }

        val verifierJwk = buildJsonObject {
            put("kty", "EC")
            put("crv", "P-256")
            put("kid", "response-key")
            put("use", "enc")
            put("alg", "ECDH-ES")
            put("key_ops", JsonArray(listOf(JsonPrimitive("deriveKey"))))
        }
        assertEquals(true, WalletPresentFunctionality2.isSupportedVerifierEncryptionJwk(verifierJwk))
        assertEquals(
            false,
            WalletPresentFunctionality2.isSupportedVerifierEncryptionJwk(
                JsonObject(verifierJwk + ("key_ops" to JsonArray(listOf(JsonPrimitive("sign")))))
            ),
        )
    }

    @Test
    fun `encrypted mdoc transcript binds verifier encryption key thumbprint`() {
        val request = AuthorizationRequest(clientId = "verifier", nonce = "nonce")
        val cleartextTranscript = MdocPresenter.buildSessionTranscript(request, "https://verifier/response", null)
        val encryptedTranscript = MdocPresenter.buildSessionTranscript(
            request,
            "https://verifier/response",
            "AQID",
        )

        assertFalse(
            coseCompliantCbor.encodeToByteArray(cleartextTranscript)
                .contentEquals(coseCompliantCbor.encodeToByteArray(encryptedTranscript))
        )
    }

    @Test
    fun `per credential presentation selects key and DID together`() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val keys = listOf("default", "selected").map { id ->
            runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
                KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)))
        }
        DidService.minimalInit()
        val dids = keys.map { DidService.registerByKey("key", it).did }
        val credential = W3C11(credentialData = buildJsonObject {},
            signature = JwtCredentialSignature("signature", buildJsonObject {}), signed = "issuer.jwt.signature")
        val query = CredentialQuery("vc", CredentialFormat.JWT_VC_JSON, meta = NoMeta)
        val matches = mapOf(query.id to listOf(DcqlMatcher.DcqlMatchResult(
            credential = RawDcqlCredential("credential", CredentialFormat.JWT_VC_JSON.id.first(),
                buildJsonObject {}, originalCredential = credential),
            selectedDisclosures = null, originalQuery = query)))
        suspend fun present(selected: CredentialPresentationKey?) = WalletPresentFunctionality2.generateVpTokenForRequest(
            authorizationRequest = AuthorizationRequest(clientId = "verifier", nonce = "nonce"),
            matchedData = matches, holderKey = null, holderDid = dids[0],
            typeRegistry = TransactionDataTypeRegistry(), verifierJwkThumbprint = null,
            holderCrypto2Key = keys[0], credentialHolderKeyResolver = { _, _ -> selected },
        )
        val missingDid = assertFailsWith<IllegalArgumentException> {
            present(CredentialPresentationKey(keys[1], did = null))
        }
        assertEquals("Missing DID for presentation", missingDid.message)
        for (selected in listOf(false, true)) {
            val did = dids[if (selected) 1 else 0]
            val signingKey = keys[if (selected) 1 else 0]
            val vpToken = Json.parseToJsonElement(present(
                if (selected) CredentialPresentationKey(signingKey, did) else null)).jsonObject
                .getValue("vc").jsonArray.single().jsonPrimitive.content
            val payload = Json.parseToJsonElement(CompactJws.verify(vpToken, signingKey, JwsAlgorithm.ES256)
                .payload.decodeToString()).jsonObject
            assertEquals(did, payload.getValue("iss").jsonPrimitive.content)
            assertEquals("nonce", payload.getValue("nonce").jsonPrimitive.content)
            assertFailsWith<Exception> {
                CompactJws.verify(vpToken, keys[if (selected) 0 else 1], JwsAlgorithm.ES256)
            }
        }
    }

    @Test
    fun `mdoc presentation requires a credential-bound holder key resolver`() = runTest {
        val walletDefault = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("wallet-default"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val credential = MdocsCredential(
            credentialData = buildJsonObject { },
            signed = null,
            docType = "org.iso.18013.5.1.mDL",
        )
        val query = CredentialQuery(
            id = "mdl",
            format = CredentialFormat.MSO_MDOC,
            meta = MsoMdocMeta(doctypeValue = credential.docType),
        )
        val matches = mapOf(
            query.id to listOf(
                DcqlMatcher.DcqlMatchResult(
                    credential = RawDcqlCredential(
                        id = "mdoc-1",
                        format = CredentialFormat.MSO_MDOC.id.first(),
                        data = buildJsonObject { },
                        originalCredential = credential,
                    ),
                    selectedDisclosures = null,
                    originalQuery = query,
                )
            )
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            WalletPresentFunctionality2.generateVpTokenForRequest(
                authorizationRequest = AuthorizationRequest(clientId = "verifier", nonce = "nonce"),
                matchedData = matches,
                holderKey = null,
                holderDid = null,
                typeRegistry = TransactionDataTypeRegistry(),
                verifierJwkThumbprint = null,
                holderCrypto2Key = walletDefault,
            )
        }

        assertEquals(
            "A credential-bound holder-key resolver is required for mdoc presentation",
            failure.message,
        )
    }

}
