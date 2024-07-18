@file:Suppress("ExtractKtorModule")

package id.walt.issuer

import COSE.AlgorithmID
import COSE.OneKey
import cbor.Cbor
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.util.X509CertUtils
import com.upokecenter.cbor.CBORObject
import id.walt.credentials.issuance.Issuer.mergingJwtIssue
import id.walt.credentials.issuance.Issuer.mergingSdJwtIssue
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.did.dids.json
import id.walt.issuer.base.config.ConfigManager
import id.walt.issuer.base.config.OIDCIssuerServiceConfig
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.ByteStringElement
import id.walt.mdoc.dataelement.DataElement
import id.walt.mdoc.dataelement.MapKey
import id.walt.mdoc.dataelement.toDE
import id.walt.mdoc.doc.MDocBuilder
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.ValidityInfo
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.CredentialSupported
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.errors.CredentialError
import id.walt.oid4vc.errors.DeferredCredentialError
import id.walt.oid4vc.interfaces.CredentialResult
import id.walt.oid4vc.providers.CredentialIssuerConfig
import id.walt.oid4vc.providers.IssuanceSession
import id.walt.oid4vc.providers.OpenIDCredentialIssuer
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.BatchCredentialRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.responses.BatchCredentialResponse
import id.walt.oid4vc.responses.CredentialErrorCode
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.util.randomUUID
import id.walt.sdjwt.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.minutes


/**
 * OIDC for Verifiable Credential Issuance service provider, implementing abstract service provider from OIDC4VC library.
 */
open class CIProvider : OpenIDCredentialIssuer(
    baseUrl = let {
        ConfigManager.getConfig<OIDCIssuerServiceConfig>().baseUrl
    }, config = CredentialIssuerConfig(credentialConfigurationsSupported = mapOf(
//        "VerifiableCredential" to listOf("VerifiableCredential"),
        "BankId" to listOf("VerifiableCredential", "BankId"),
        "KycChecksCredential" to listOf("VerifiableCredential", "VerifiableAttestation", "KycChecksCredential"),
        "KycDataCredential" to listOf("VerifiableCredential", "VerifiableAttestation", "KycDataCredential"),
        "PassportCh" to listOf("VerifiableCredential", "VerifiableAttestation", "VerifiableId", "PassportCh"),
        "PND91Credential" to listOf("VerifiableCredential", "PND91Credential"),
        "MortgageEligibility" to listOf(
            "VerifiableCredential",
            "VerifiableAttestation",
            "VerifiableId",
            "MortgageEligibility"
        ),
        "PortableDocumentA1" to listOf("VerifiableCredential", "VerifiableAttestation", "PortableDocumentA1"),
        "OpenBadgeCredential" to listOf("VerifiableCredential", "OpenBadgeCredential"),
        "VaccinationCertificate" to listOf(
            "VerifiableCredential",
            "VerifiableAttestation",
            "VaccinationCertificate"
        ),
        "WalletHolderCredential" to listOf("VerifiableCredential", "WalletHolderCredential"),
        "UniversityDegree" to listOf("VerifiableCredential", "UniversityDegree"),
        "VerifiableId" to listOf("VerifiableCredential", "VerifiableAttestation", "VerifiableId"),
        "CTWalletSameAuthorisedInTime" to listOf(
            "VerifiableCredential",
            "VerifiableAttestation",
            "CTWalletSameAuthorisedInTime"
        ),
        "CTWalletSameAuthorisedDeferred" to listOf(
            "VerifiableCredential",
            "VerifiableAttestation",
            "CTWalletSameAuthorisedDeferred"
        ),
        "CTWalletSamePreAuthorisedInTime" to listOf(
            "VerifiableCredential",
            "VerifiableAttestation",
            "CTWalletSamePreAuthorisedInTime"
        ),
        "CTWalletSamePreAuthorisedDeferred" to listOf(
            "VerifiableCredential",
            "VerifiableAttestation",
            "CTWalletSamePreAuthorisedDeferred"
        ),

        "AlpsTourReservation" to listOf("VerifiableCredential", "VerifiableAttestation", "AlpsTourReservation"),
        "EducationalID" to listOf("VerifiableCredential", "VerifiableAttestation", "EducationalID"),
        "HotelReservation" to listOf("VerifiableCredential", "VerifiableAttestation", "HotelReservation"),
        "Iso18013DriversLicenseCredential" to listOf(
            "VerifiableCredential",
            "VerifiableAttestation",
            "Iso18013DriversLicenseCredential"
        ),
        "TaxReceipt" to listOf("VerifiableCredential", "VerifiableAttestation", "TaxReceipt"),
        "VerifiablePortableDocumentA1" to listOf(
            "VerifiableCredential",
            "VerifiableAttestation",
            "VerifiablePortableDocumentA1"
        ),
    ).flatMap { entry ->
        CredentialFormat.values().map { format -> Pair(
            "${entry.key}_${format.value}",
            CredentialSupported(
                format = format,
                cryptographicBindingMethodsSupported = setOf("did"),
                cryptographicSuitesSupported = setOf("EdDSA", "ES256", "ES256K", "RSA"),
                types = entry.value
            ))
        }
    }.plus(Pair("org.iso.18013.5.1.mDL", CredentialSupported(
        format = CredentialFormat.mso_mdoc,
        cryptographicBindingMethodsSupported = setOf("cose_key"),
        cryptographicSuitesSupported = setOf("ES256"),
        types = listOf("org.iso.18013.5.1.mDL"),
        docType = "org.iso.18013.5.1.mDL"
    ))).plus(
        Pair("urn:eu.europa.ec.eudi:pid:1", CredentialSupported(
        format = CredentialFormat.sd_jwt_vc,
            cryptographicBindingMethodsSupported = null,
            cryptographicSuitesSupported = setOf("ES256"),
            types = listOf("urn:eu.europa.ec.eudi:pid:1"),
            docType = "urn:eu.europa.ec.eudi:pid:1"
    )
        )
    ).toMap())
) {
    companion object {

        val exampleIssuerKey by lazy { runBlocking { JWKKey.generate(KeyType.Ed25519) } }
        val exampleIssuerDid by lazy { runBlocking { DidService.registerByKey("jwk", exampleIssuerKey).did } }


        private val CI_TOKEN_KEY by lazy { runBlocking { JWKKey.generate(KeyType.Ed25519) } }
    }

    // -------------------------------
    // Simple in-memory session management
    private val authSessions: MutableMap<String, IssuanceSession> = mutableMapOf()


    var deferIssuance = false
    val deferredCredentialRequests = mutableMapOf<String, CredentialRequest>()
    override fun getSession(id: String): IssuanceSession? {
        println("RETRIEVING CI AUTH SESSION: $id")
        return authSessions[id]
    }

    override fun getSessionByIdTokenRequestState(idTokenRequestState: String): IssuanceSession? {
        println("RETRIEVING CI AUTH SESSION by idTokenRequestState: $idTokenRequestState")
        var properSession: IssuanceSession? = null
        authSessions.forEach { entry ->
            print("${entry.key} : ${entry.value}")
            val session = entry.value
            if (session.idTokenRequestState == idTokenRequestState) {
                properSession = session
            }
        }
        return properSession
    }


    override fun putSession(id: String, session: IssuanceSession): IssuanceSession? {
        println("SETTING CI AUTH SESSION: $id = $session")
        return authSessions.put(id, session)
    }

    override fun removeSession(id: String): IssuanceSession? {
        println("REMOVING CI AUTH SESSION: $id")
        return authSessions.remove(id)
    }


    // ------------------------------------------
    // Simple cryptographics operation interface implementations
    override fun signToken(
        target: TokenTarget,
        payload: JsonObject,
        header: JsonObject?,
        keyId: String?,
        privKey: Key?
    ) =
        runBlocking {
            println("Signing JWS:   $payload")
            println("JWS Signature: target: $target, keyId: $keyId, header: $header")
            if (header != null && keyId != null && privKey != null) {
                val headers = header.toMutableMap().plus(mapOf("alg" to "ES256".toJsonElement(), "type" to "jwt".toJsonElement(), "kid" to keyId.toJsonElement()))
                privKey.signJws(payload.toString().toByteArray(), headers).also {
                    println("Signed JWS: >> $it")
                }

            } else {
                CI_TOKEN_KEY.signJws(payload.toString().toByteArray()).also {
                    println("Signed JWS: >> $it")
                }
            }
        }

    @OptIn(ExperimentalEncodingApi::class)
    override fun verifyTokenSignature(target: TokenTarget, token: String) = runBlocking {
        println("Verifying JWS: $token")
        println("JWS Verification: target: $target")

        val tokenHeader = Json.parseToJsonElement(Base64.decode(token.split(".")[0]).decodeToString()).jsonObject
        val key = if(tokenHeader["jwk"] != null) {
            JWKKey.importJWK(tokenHeader["jwk"].toString()).getOrThrow()
        }
        else if (tokenHeader["kid"] != null) {
            val did = tokenHeader["kid"]!!.jsonPrimitive.content.split("#")[0]
            println("Resolving DID: $did")
            DidService.resolveToKey(did).getOrThrow()
        } else {
            CI_TOKEN_KEY
        }
        key.verifyJws(token).also { println("VERIFICATION IS: $it") }
    }.isSuccess

    override fun verifyCOSESign1Signature(target: TokenTarget, token: String) = runBlocking {
        println("Verifying JWS: $token")
        println("JWS Verification: target: $target")
        val coseSign1 = Cbor.decodeFromByteArray<COSESign1>(token.base64UrlDecode())
        val tokenHeader = coseSign1.decodeProtectedHeader()
        val rawKey = (tokenHeader.value[MapKey(ProofOfPossession.CWTProofBuilder.HEADER_LABEL_COSE_KEY)] as ByteStringElement).value
        val cryptoProvider = SimpleCOSECryptoProvider(listOf(COSECryptoProviderKeyInfo("pub-key", AlgorithmID.ECDSA_256,
            OneKey(CBORObject.DecodeFromBytes(rawKey)).AsPublicKey()
        )))
        cryptoProvider.verify1(coseSign1, "pub-key")
//        if (tokenHeader.value[MapKey] != null) {
//            val did = tokenHeader["kid"]!!.jsonPrimitive.content.split("#")[0]
//            println("Resolving DID: $did")
//            val key = DidService.resolveToKey(did).getOrThrow()
//            key.verifyJws(token).also { println("VERIFICATION IS: $it") }
//        } else {
//            CI_TOKEN_KEY.verifyJws(token)
//        }
    }

    // -------------------------------------
    // Implementation of abstract issuer service provider interface
    @OptIn(ExperimentalEncodingApi::class)
    override fun generateCredential(credentialRequest: CredentialRequest): CredentialResult {
        println("GENERATING CREDENTIAL:")
        println("Credential request: $credentialRequest")
        println("CREDENTIAL REQUEST JSON -------:")
        println(Json.encodeToString(credentialRequest))

        if (deferIssuance) return CredentialResult(credentialRequest.format, null, randomUUID()).also {
            deferredCredentialRequests[it.credentialId!!] = credentialRequest
        }
        return when(credentialRequest.format) {
            CredentialFormat.mso_mdoc -> doGenerateMDoc(credentialRequest)
            else -> doGenerateCredential(credentialRequest)
        }
    }

    override fun getDeferredCredential(credentialID: String): CredentialResult {
        return deferredCredentialRequests[credentialID]?.let { when(it.format) {
            CredentialFormat.mso_mdoc -> doGenerateMDoc(it)
            else -> doGenerateCredential(it)
        } }
            ?: throw DeferredCredentialError(CredentialErrorCode.invalid_request, message = "Invalid credential ID given")
    }

    private fun doGenerateCredential(
        credentialRequest: CredentialRequest
    ): CredentialResult {
        if (credentialRequest.format == CredentialFormat.mso_mdoc) throw CredentialError(
            credentialRequest, CredentialErrorCode.unsupported_credential_format
        )

        val proofPayload = credentialRequest.proof?.jwt?.let { parseTokenPayload(it) } ?: throw CredentialError(
            credentialRequest, CredentialErrorCode.invalid_or_missing_proof, message = "Proof must be JWT proof"
        )
        val proofHeader = credentialRequest.proof?.jwt?.let { parseTokenHeader(it) } ?: throw CredentialError(
            credentialRequest, CredentialErrorCode.invalid_or_missing_proof, message = "Proof must be JWT proof"
        )

        val holderKid = proofHeader[JWTClaims.Header.keyID]?.jsonPrimitive?.content
        val holderKey = proofHeader[JWTClaims.Header.jwk]?.jsonObject

        if(holderKey.isNullOrEmpty() && holderKid.isNullOrEmpty()) throw CredentialError(
            credentialRequest,
            CredentialErrorCode.invalid_or_missing_proof,
            message = "Proof JWT header must contain kid or jwk claim"
        )
        val holderDid = if (!holderKid.isNullOrEmpty() && DidUtils.isDidUrl(holderKid)) holderKid.substringBefore("#") else holderKid
        val nonce = proofPayload["nonce"]?.jsonPrimitive?.content ?: throw CredentialError(
            credentialRequest,
            CredentialErrorCode.invalid_or_missing_proof, message = "Proof must contain nonce")

        println("RETRIEVING VC FROM TOKEN MAPPING: $nonce")
        val data: IssuanceSessionData = tokenCredentialMapping[nonce]?.first()
            ?: throw IllegalArgumentException("The issuanceIdCredentialMapping does not contain a mapping for: $nonce!")

        return CredentialResult(format = credentialRequest.format, credential = JsonPrimitive(runBlocking {
            val vc = data.request.credentialData ?: throw MissingFieldException(listOf("credentialData"), "credentialData")

            data.run {
                var issuerKid = issuerDid
                if (issuerDid.startsWith("did:key") && issuerDid.length == 186) // EBSI conformance corner case when issuer uses did:key instead of did:ebsi and no trust framework is defined
                    issuerKid = issuerDid + "#" + issuerDid.removePrefix("did:key:")

                if (issuerDid.startsWith("did:ebsi"))
                    issuerKid = issuerDid + "#" + issuerKey.getKeyId()

                when (credentialRequest.format) {
                    CredentialFormat.sd_jwt_vc -> (holderKey?.let {
                        SDJwtVC.sign(SDPayload.Companion.createSDPayload(vc.toJsonObject(), buildJsonObject {}),
                            WaltIdJWTCryptoProvider(mapOf(issuerKey.getKeyId() to issuerKey)),
                            issuerDid = issuerDid, holderKeyJWK = holderKey, issuerKeyId = issuerKey.getKeyId(),
                            vct = data.request.credentialConfigurationId) } ?:
                        SDJwtVC.sign(SDPayload.Companion.createSDPayload(vc.toJsonObject(), buildJsonObject {}),
                            WaltIdJWTCryptoProvider(mapOf(issuerKey.getKeyId() to issuerKey)),
                            issuerDid = issuerDid, holderDid = holderDid!!, issuerKeyId = issuerKey.getKeyId(),
                            vct = data.request.credentialConfigurationId)).toString()
                    //
                    //                    vc.mergingSdJwtIssue(
//                        issuerKey = issuerKey,
//                        issuerDid = issuerDid,
//                        subjectDid = holderDid ?: "",
//                        mappings = request.mapping ?: JsonObject(emptyMap()),
//                        additionalJwtHeader = emptyMap(),
//                        additionalJwtOptions = holderKey?.let { mapOf(
//                            "cnf" to buildJsonObject { put("jwk", it) }
//                        ) } ?: emptyMap(),
//                        disclosureMap = data.request.selectiveDisclosure ?: SDMap.Companion.generateSDMap(
//                            JsonObject(emptyMap()),
//                            JsonObject(emptyMap())
//                        )
//                    )

                    else -> vc.mergingJwtIssue(
                        issuerKey = issuerKey,
                        issuerDid = issuerDid,
                        issuerKid = issuerKid,
                        subjectDid = holderDid ?: "",
                        mappings = request.mapping ?: JsonObject(emptyMap()),
                        additionalJwtHeader = emptyMap(),
                        additionalJwtOptions = holderKey?.let { mapOf(
                            "cnf" to buildJsonObject { put("jwk", it) }
                        ) } ?: emptyMap(),
                    )
                }
            }.also { println("Respond VC: $it") }
        }))
    }

    private fun doGenerateMDoc(
        credentialRequest: CredentialRequest
    ): CredentialResult {
        val coseSign1 = Cbor.decodeFromByteArray<COSESign1>(credentialRequest.proof?.cwt?.base64UrlDecode() ?: throw CredentialError(credentialRequest,
            CredentialErrorCode.invalid_or_missing_proof, message = "No CWT proof found on credential request"))
        val tokenHeader = coseSign1.decodeProtectedHeader()
        val rawHolderKey = (tokenHeader.value[MapKey(ProofOfPossession.CWTProofBuilder.HEADER_LABEL_COSE_KEY)] as ByteStringElement).value
        val nonce = getNonceFromProof(credentialRequest.proof!!) ?: throw CredentialError(credentialRequest, CredentialErrorCode.invalid_or_missing_proof, message = "No nonce found on proof")
        println("RETRIEVING VC FROM TOKEN MAPPING: $nonce")
        val data: IssuanceSessionData = tokenCredentialMapping[nonce]?.first()
            ?: throw CredentialError(credentialRequest, CredentialErrorCode.invalid_request,"The issuanceIdCredentialMapping does not contain a mapping for: $nonce!")
        val issuerSignedItems = data.request.mdocData ?: throw MissingFieldException(listOf("mdocData"), "mdocData")
        val issuerKey = JWK.parse(runBlocking { data.issuerKey.exportJWK() }).toECKey()
        val keyId = runBlocking { data.issuerKey.getKeyId() }
        val cryptoProvider = SimpleCOSECryptoProvider(listOf(
            COSECryptoProviderKeyInfo(
                keyId, AlgorithmID.ECDSA_256, issuerKey.toECPublicKey(), issuerKey.toECPrivateKey(),
                x5Chain =  data.request.x5Chain?.map { X509CertUtils.parse(it) } ?: listOf(),
                trustedRootCAs = data.request.trustedRootCAs?.map { X509CertUtils.parse(it) } ?: listOf()
            )
        ))
        val mdoc = MDocBuilder(credentialRequest.docType
            ?: throw CredentialError(credentialRequest, CredentialErrorCode.invalid_request, message = "Missing doc type in credential request")
        ).apply {
            issuerSignedItems.forEach { namespace -> namespace.value.forEach { property ->
                addItemToSign(namespace.key, property.key, property.value.toDE())
            }}
        }.sign( // TODO: expiration date!
            ValidityInfo(Clock.System.now(), Clock.System.now(), Clock.System.now().plus(365*24, DateTimeUnit.HOUR)),
            DeviceKeyInfo(DataElement.fromCBOR(
                OneKey(CBORObject.DecodeFromBytes(rawHolderKey)).AsCBOR().EncodeToBytes()
            )), cryptoProvider, keyId
        )
        return CredentialResult(CredentialFormat.mso_mdoc, JsonPrimitive(mdoc.issuerSigned.toMapElement().toCBOR().encodeToBase64Url()),
            customParameters = mapOf("credential_encoding" to JsonPrimitive("issuer-signed"))
        )
    }


    @OptIn(ExperimentalEncodingApi::class)
    override fun generateBatchCredentialResponse(
        batchCredentialRequest: BatchCredentialRequest,
        accessToken: String
    ): BatchCredentialResponse {
        if (batchCredentialRequest.credentialRequests.map { it.format }.distinct().size >= 2) {
            throw IllegalArgumentException("Credential request don't have the same format")
        }

        val keyIdsDistinct = batchCredentialRequest.credentialRequests.map { credReq ->
            credReq.proof?.jwt?.let { jwt -> parseTokenHeader(jwt) }
                ?.get(JWTClaims.Header.keyID)
                ?.jsonPrimitive?.content
                ?: throw CredentialError(
                    credReq,
                    CredentialErrorCode.invalid_or_missing_proof,
                    message = "Proof must be JWT proof"
                )
        }.distinct()

        if (keyIdsDistinct.size >= 2) {
            throw IllegalArgumentException("More than one key id requested")
        }

//        val keyId = keyIdsDistinct.first()


        batchCredentialRequest.credentialRequests.first().let { credentialRequest ->
            val jwt =
                credentialRequest.proof?.jwt ?: throw IllegalArgumentException("No proof.jwt in credential request!")
            val jwtParts = jwt.split(".")

            fun decodeJwtPart(idx: Int) =
                Json.parseToJsonElement(Base64.decode(jwtParts[idx]).decodeToString()).jsonObject

            val header = decodeJwtPart(0)
            val payload = decodeJwtPart(1)

            val subjectDid =
                header["kid"]?.jsonPrimitive?.contentOrNull
                    ?: throw IllegalArgumentException("No kid in proof.jwt header!")
            val nonce = payload["nonce"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("No nonce in proof.jwt payload!")

            println("RETRIEVING VC FROM TOKEN MAPPING: $nonce")
            val issuanceSessionData = tokenCredentialMapping[nonce]
                ?: throw IllegalArgumentException("The issuanceIdCredentialMapping does not contain a mapping for: $nonce!")

            val credentialResults = issuanceSessionData.map { data ->
                CredentialResponse.success(
                    format = credentialRequest.format,
                    credential = JsonPrimitive(
                        runBlocking {
                            val vc = data.request.credentialData ?: throw MissingFieldException(listOf("credentialData"), "credentialData")

                            data.run {
                                when (credentialRequest.format) {
                                    CredentialFormat.sd_jwt_vc -> vc.mergingSdJwtIssue(
                                        issuerKey = issuerKey,
                                        issuerDid = issuerDid,
                                        subjectDid = subjectDid,
                                        mappings = request.mapping ?: JsonObject(emptyMap()),
                                        additionalJwtHeader = emptyMap(),
                                        additionalJwtOptions = emptyMap(),
                                        disclosureMap = data.request.selectiveDisclosure
                                            ?: SDMap.Companion.generateSDMap(
                                                JsonObject(emptyMap()),
                                                JsonObject(emptyMap())
                                            )
                                    )

                                    else -> vc.mergingJwtIssue(
                                        issuerKey = issuerKey,
                                        issuerDid = issuerDid,
                                        subjectDid = subjectDid,
                                        mappings = request.mapping ?: JsonObject(emptyMap()),
                                        additionalJwtHeader = emptyMap(),
                                        additionalJwtOptions = emptyMap(),
                                    )
                                }

                            }.also { println("Respond VC: $it") }
                        }
                    )
                )
            }

            return BatchCredentialResponse.success(credentialResults, accessToken, 5.minutes)
        }
    }


    data class IssuanceSessionData(
        val issuerKey: Key, val issuerDid: String, val request: IssuanceRequest
    )

    // TODO: Hack as this is non stateless because of oidc4vc lib API
    val sessionCredentialPreMapping = HashMap<String, List<IssuanceSessionData>>() // session id -> VC

    // TODO: Hack as this is non stateless because of oidc4vc lib API
    private val tokenCredentialMapping = HashMap<String, List<IssuanceSessionData>>() // token -> VC

    //private val sessionTokenMapping = HashMap<String, String>() // session id -> token

    // TODO: Hack as this is non stateless because of oidc4vc lib API
    fun setIssuanceDataForIssuanceId(issuanceId: String, data: List<IssuanceSessionData>) {
        println("DEPOSITED CREDENTIAL FOR ISSUANCE ID: $issuanceId")
        sessionCredentialPreMapping[issuanceId] = data
    }

    // TODO: Hack as this is non stateless because of oidc4vc lib API
    fun mapSessionIdToToken(sessionId: String, token: String) {
        println("MAPPING SESSION ID TO TOKEN: $sessionId -->> $token")
        val premappedVc = sessionCredentialPreMapping.remove(sessionId)
            ?: throw IllegalArgumentException("No credential pre-mapped with any such session id: $sessionId (for use with token: $token)")
        println("SWAPPING PRE-MAPPED VC FROM SESSION ID TO NEW TOKEN: $token")
        tokenCredentialMapping[token] = premappedVc
    }

    fun mapSessionIdToIdAuthRequestState(sessionId: String, token: String) {
        println("MAPPING SESSION ID TO TOKEN: $sessionId -->> $token")
        val premappedVc = sessionCredentialPreMapping.remove(sessionId)
            ?: throw IllegalArgumentException("No credential pre-mapped with any such session id: $sessionId (for use with token: $token)")
        println("SWAPPING PRE-MAPPED VC FROM SESSION ID TO NEW TOKEN: $token")
        tokenCredentialMapping[token] = premappedVc
    }
}
