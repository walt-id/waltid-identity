package id.walt.sdjwt

import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

class SDJwtVC(sdJwt: SDJwt) :
    SDJwt(
        sdJwt.jwt,
        sdJwt.header,
        sdJwt.sdPayload,
        sdJwt.keyBindingJwt,
        sdJwt.isPresentation
    ) {

    val cnfObject: JsonObject? = undisclosedPayload["cnf"]?.jsonObject
    val holderDid: String? = cnfObject?.get("kid")?.jsonPrimitive?.content
    val holderKeyJWK: JsonObject? = cnfObject?.get("jwk")?.jsonObject
    val issuer = undisclosedPayload["iss"]?.jsonPrimitive?.content
    val notBefore = undisclosedPayload["nbf"]?.jsonPrimitive?.long
    val expiration = undisclosedPayload["exp"]?.jsonPrimitive?.long
    val vct = undisclosedPayload["vct"]?.jsonPrimitive?.content
    val status = undisclosedPayload["status"]?.jsonObject
    val sdAlg = undisclosedPayload["_sd_alg"]?.jsonPrimitive?.content

    private fun verifyHolderKeyBinding(
        jwtCryptoProvider: JWTCryptoProvider,
        requiresHolderKeyBinding: Boolean,
        audience: String? = null,
        nonce: String? = null
    ): Boolean {
        return if (!holderDid.isNullOrEmpty()) TODO("Holder DID verification not yet supported")
        else if (holderKeyJWK != null) {
            isPresentation && keyBindingJwt != null && !audience.isNullOrEmpty() && !nonce.isNullOrEmpty() &&
                    keyBindingJwt.verifyKB(
                        jwtCryptoProvider = jwtCryptoProvider,
                        reqAudience = audience,
                        reqNonce = nonce,
                        sdJwt = this,
                        keyId = holderKeyJWK["kid"]?.jsonPrimitive?.content
                    )
        } else
            !requiresHolderKeyBinding
    }

    // TODO: issuer DID/key needs to be resolved outside, to initialize the required crypto provider. Needs improvement!
    // TODO: should resolve issuer key from "iss" property
    // TODO: make use of Key interface from waltid-crypto lib instead or also?
    fun verifyVC(
        jwtCryptoProvider: JWTCryptoProvider,
        requiresHolderKeyBinding: Boolean,
        audience: String? = null,
        nonce: String? = null
    ): VCVerificationResult {
        var message = ""

        return VCVerificationResult(
            sdJwtVC = this,
            sdJwtVerificationResult = verify(
                jwtCryptoProvider = jwtCryptoProvider,
                keyID = header["kid"]?.jsonPrimitive?.content ?: issuer
            ),
            sdJwtVCVerified =
                (notBefore?.let { Clock.System.now().epochSeconds >= it } ?: true).also {
                    if (!it) message = "$message, VC is not valid before $notBefore"
                } &&
                        (expiration?.let { Clock.System.now().epochSeconds < it } ?: true).also {
                            if (!it) message = "$message, VC is not valid after $expiration"
                        } &&
                        !vct.isNullOrEmpty().also {
                            if (it) message = "$message, VC has no verifiable credential type property (vct)"
                        } &&
                        verifyHolderKeyBinding(jwtCryptoProvider, requiresHolderKeyBinding, audience, nonce).also {
                            if (!it) message = "$message, holder key binding could not be verified"
                        },
            vcVerificationMessage = message
        )
    }

    companion object {
        const val SD_JWT_VC_TYPE_HEADER = "vc+sd-jwt"

        fun parse(sdJwt: String) = SDJwtVC(SDJwt.parse(sdJwt))

        /**
         * Parse SD-JWT VC from a token string and verify it
         * @return SD-JWT VC verification result, with parsed SD-JWT VC
         * @throws Exception if SD-JWT VC cannot be parsed
         */
        fun parseAndVerify(
            sdJwtVC: String,
            jwtCryptoProvider: JWTCryptoProvider,
            requiresHolderKeyBinding: Boolean,
            audience: String? = null,
            nonce: String? = null
        ): VCVerificationResult {
            val parsedVC = parse(sdJwtVC)
            return parsedVC.verifyVC(
                jwtCryptoProvider = jwtCryptoProvider,
                requiresHolderKeyBinding = requiresHolderKeyBinding,
                audience = audience,
                nonce = nonce
            )
        }

        fun sign(
            sdPayload: SDPayload,
            jwtCryptoProvider: JWTCryptoProvider,
            issuerDid: String,
            holderDid: String,
            issuerKeyId: String? = null,
            vct: String,
            nbf: Long? = null,
            exp: Long? = null,
            status: JsonObject? = null,
            /** Set additional options in the JWT header */
            additionalJwtHeader: Map<String, Any> = emptyMap(),
            subject: String? = null
        ): SDJwtVC = doSign(
            sdPayload = sdPayload,
            jwtCryptoProvider = jwtCryptoProvider,
            issuerDid = issuerDid,
            cnf = buildJsonObject {
                put("kid", holderDid)
            },
            issuerKeyId = issuerKeyId,
            vct = vct,
            nbf = nbf,
            exp = exp,
            status = status,
            additionalJwtHeader = additionalJwtHeader,
            subject = subject
        )

        fun sign(
            sdPayload: SDPayload,
            jwtCryptoProvider: JWTCryptoProvider,
            issuerDid: String,
            holderKeyJWK: JsonObject,
            issuerKeyId: String? = null,
            vct: String,
            nbf: Long? = null,
            exp: Long? = null,
            status: JsonObject? = null,
            /** Set additional options in the JWT header */
            additionalJwtHeader: Map<String, Any> = emptyMap(),
            subject: String? = null
        ): SDJwtVC = doSign(
            sdPayload = sdPayload,
            jwtCryptoProvider = jwtCryptoProvider,
            issuerDid = issuerDid,
            cnf = buildJsonObject {
                put("jwk", holderKeyJWK)
            },
            issuerKeyId = issuerKeyId,
            vct = vct,
            nbf = nbf,
            exp = exp,
            status = status,
            additionalJwtHeader = additionalJwtHeader,
            subject = subject
        )

        fun sign(
            sdPayload: SDPayload,
            jwtCryptoProvider: JWTCryptoProvider,
            issuerDid: String,
            holderDid: String?,
            holderKeyJWK: JsonObject?,
            issuerKeyId: String? = null,
            vct: String,
            nbf: Long? = null,
            exp: Long? = null,
            status: JsonObject? = null,
            /** Set additional options in the JWT header */
            additionalJwtHeader: Map<String, Any> = emptyMap()
        ): SDJwtVC = holderDid?.let {
            sign(sdPayload, jwtCryptoProvider, issuerDid, it, issuerKeyId, vct, nbf, exp, status, additionalJwtHeader)
        } ?: holderKeyJWK?.let {
            sign(sdPayload, jwtCryptoProvider, issuerDid, it, issuerKeyId, vct, nbf, exp, status, additionalJwtHeader)
        } ?: throw IllegalArgumentException("Either holderKey or holderDid must be given")

        private fun doSign(
            sdPayload: SDPayload,
            jwtCryptoProvider: JWTCryptoProvider,
            issuerDid: String,
            cnf: JsonObject,
            issuerKeyId: String? = null,
            vct: String,
            nbf: Long? = null,
            exp: Long? = null,
            status: JsonObject? = null,
            /** Set additional options in the JWT header */
            additionalJwtHeader: Map<String, Any> = emptyMap(),
            subject: String? = null
        ): SDJwtVC {
            val undisclosedPayload = sdPayload.undisclosedPayload.plus(
                defaultPayloadProperties(
                    issuerId = issuerDid,
                    cnf = cnf,
                    vct = vct,
                    notBefore = nbf,
                    expirationDate = exp,
                    status = status,
                    subject = subject
                )
            ).let { JsonObject(it) }

            val finalSdPayload = SDPayload(undisclosedPayload, sdPayload.digestedDisclosures)

            return SDJwtVC(
                sdJwt = sign(
                    sdPayload = finalSdPayload,
                    jwtCryptoProvider = jwtCryptoProvider,
                    keyID = issuerKeyId,
                    typ = SD_JWT_VC_TYPE_HEADER,
                    additionalHeaders = additionalJwtHeader
                )
            )
        }

        fun defaultPayloadProperties(
            issuerId: String,
            cnf: JsonObject,
            vct: String,
            notBefore: Long? = null,
            expirationDate: Long? = null,
            status: JsonObject? = null,
            subject: String? = null
        ) = buildJsonObject {
            put("_sd_alg", "sha-256")
            put("iss", JsonPrimitive(issuerId))
            put("cnf", cnf)
            put("vct", JsonPrimitive(vct))
            notBefore?.let { put("nbf", JsonPrimitive(it)) }
            expirationDate?.let { put("exp", JsonPrimitive(it)) }
            status?.let { put("status", it) }
            subject?.let {
                put("sub", JsonPrimitive(it))
            }
        }

        fun isSdJwtVCPresentation(token: String): Boolean = parse(token).isPresentation
    }
}
