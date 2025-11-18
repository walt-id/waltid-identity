package id.walt.sdjwt

import id.walt.sdjwt.utils.SdjwtStringUtils.decodeFromBase64Url
import kotlinx.serialization.json.*
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * SD-JWT object, providing signed JWT token, header and payload with disclosures, as well as optional holder binding
 * @param keyBindingJwt adds the provided key binding JWT as a holder key proof-of-possession the presented SD-JWT token
 */
@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
open class SDJwt internal constructor(
    val jwt: String,
    val header: JsonObject,
    val sdPayload: SDPayload,
    val keyBindingJwt: KeyBindingJwt? = null,
    val isPresentation: Boolean = false
) {
    internal constructor(sdJwt: SDJwt) : this(
        sdJwt.jwt,
        sdJwt.header,
        sdJwt.sdPayload,
        sdJwt.keyBindingJwt,
        sdJwt.isPresentation
    )

    /**
     * Encoded disclosures, included in this SD-JWT
     */
    @JsName("zzz_unused_disclosures") // redefined in SDJwtJS
    val disclosures
        get() = sdPayload.sDisclosures.map { it.disclosure }.toSet()

    @JsName("zzz_unused_disclosureObjects") // redefined in SDJwtJS
    val disclosureObjects
        get() = sdPayload.sDisclosures

    @JsName("zzz_unused_undisclosedPayload") // redefined in SDJwtJS
    val undisclosedPayload
        get() = sdPayload.undisclosedPayload

    @JsName("zzz_unused_fullPayload") // redefined in SDJwtJS
    val fullPayload
        get() = sdPayload.fullPayload

    @JsName("zzz_unused_digestedDisclosures")
    val digestedDisclosures
        get() = sdPayload.digestedDisclosures

    @JsName("zzz_unused_sdMap")
    val sdMap
        get() = sdPayload.sdMap

    /**
     * Signature algorithm from JWT header
     */
    val algorithm
        get() = header["alg"]?.jsonPrimitive?.contentOrNull

    /**
     * Signature key ID from the JWT header, if present
     */
    val keyID
        get() = header["kid"]?.jsonPrimitive?.contentOrNull

    /**
     * Signature key in JWK format, from JWT header, if present
     */
    val jwk
        get() = header["jwk"]?.jsonPrimitive?.contentOrNull

    val type
        get() = header["typ"]?.jsonPrimitive?.contentOrNull

    val x5c
        get() = header["x5c"]?.jsonArray?.map { it.jsonPrimitive.content }

    override fun toString() = toString(isPresentation)

    @JsName("toFormattedString")
    open fun toString(formatForPresentation: Boolean, withKBJwt: Boolean = true): String {

        val sdJwtParts = mutableListOf<String>()

        sdJwtParts.add(jwt)
        sdJwtParts.addAll(disclosures)

        if (withKBJwt && keyBindingJwt != null) {
            sdJwtParts.add(keyBindingJwt.toString())
        } else if (formatForPresentation) {
            sdJwtParts.add("")
        }

        return sdJwtParts.joinToString(SEPARATOR_STR)
    }

    /**
     * Present SD-JWT with the selection of disclosures
     * @param sdMap Selective disclosure map, indicating for each field (recursively) whether it should be disclosed or undisclosed in the presentation
     * @param withKBJwt Optionally, adds the provided key binding JWT as a holder key proof-of-possession (PoP) the presented SD-JWT token
     */
    @JsName("present")
    fun present(sdMap: SDMap?, withKBJwt: KeyBindingJwt? = null): SDJwt {
        return SDJwt(
            jwt = jwt,
            header = header,
            sdPayload = sdMap?.let { sdPayload.withSelectiveDisclosures(it) } ?: sdPayload.withoutDisclosures(),
            keyBindingJwt = withKBJwt ?: keyBindingJwt, isPresentation = true)
    }

    /**
     * Shortcut to presenting the SD-JWT, with all disclosures selected or unselected
     * @param discloseAll true: disclose all selective disclosures, false: all selective disclosures remain undisclosed
     * @param withKBJwt Optionally, adds the provided key binding JWT as a holder key proof-of-possession to the presented SD-JWT token
     */
    @JsName("presentAll")
    fun present(discloseAll: Boolean, withKBJwt: KeyBindingJwt? = null): SDJwt {
        return SDJwt(
            jwt = jwt,
            header = header,
            sdPayload = if (discloseAll) {
                sdPayload
            } else {
                sdPayload.withoutDisclosures()
            },
            keyBindingJwt = withKBJwt ?: keyBindingJwt,
            isPresentation = true
        )
    }

    /**
     * Present SD-JWT with the selection of disclosures
     * @param sdMap Selective disclosure map, indicating for each field (recursively) whether it should be disclosed or undisclosed in the presentation
     * @param audience  Audience to set in the required "aud" property of the key binding jwt body
     * @param nonce   Nonce value for the required "nonce" property of the key binding jwt body
     * @param kbCryptoProvider  Crypto provider to sign the JWT with the given holder key
     * @param kbKeyId Optional key ID of the key to be used for signature, if required by crypto provider
     */
    @JsName("presentWithKB")
    fun present(
        sdMap: SDMap?,
        audience: String,
        nonce: String,
        kbCryptoProvider: JWTCryptoProvider,
        kbKeyId: String? = null
    ) =
        present(
            sdMap = sdMap,
            withKBJwt = KeyBindingJwt.sign(
                presentedSdJwt = present(sdMap).toString(),
                audience = audience,
                nonce = nonce,
                cryptoProvider = kbCryptoProvider,
                keyId = kbKeyId
            )
        )

    /**
     * Shortcut to presenting the SD-JWT, with all disclosures selected or unselected
     * @param discloseAll true: disclose all selective disclosures, false: all selective disclosures remain undisclosed
     * @param audience  Audience to set in the required "aud" property of the key binding jwt body
     * @param nonce   Nonce value for the required "nonce" property of the key binding jwt body
     * @param kbCryptoProvider  Crypto provider to sign the JWT with the given holder key
     * @param kbKeyId Optional key ID of the key to be used for signature, if required by crypto provider
     */
    @JsName("presentAllWithKB")
    fun present(
        discloseAll: Boolean,
        audience: String,
        nonce: String,
        kbCryptoProvider: JWTCryptoProvider,
        kbKeyId: String? = null
    ) =
        present(
            discloseAll = discloseAll,
            withKBJwt = KeyBindingJwt.sign(
                presentedSdJwt = present(
                    discloseAll
                ).toString(),
                audience = audience,
                nonce = nonce,
                cryptoProvider = kbCryptoProvider,
                keyId = kbKeyId
            )
        )

    /**
     * TODO: make use of Key interface from waltid-crypto lib instead or also?
     * Verify the SD-JWT by checking the signature, using the given JWT crypto provider, and matching the disclosures against the digests in the JWT payload
     * @param jwtCryptoProvider JWT Crypto Provider that implements standard JWT token verification on the target platform
     */
    fun verify(jwtCryptoProvider: JWTCryptoProvider, keyID: String? = null): VerificationResult<SDJwt> {
        return jwtCryptoProvider.verify(jwt, keyID ?: this.keyID).let {
            VerificationResult(
                sdJwt = this,
                signatureVerified = it.verified,
                disclosuresVerified = sdPayload.verifyDisclosures(),
                message = it.message
            )
        }
    }

    /**
     * Verify the SD-JWT by checking the signature, using the given JWT crypto provider, and matching the disclosures against the digests in the JWT payload
     * @param jwtCryptoProvider JWT Crypto Provider that implements standard JWT token verification on the target platform
     */
    @JsExport.Ignore
    suspend fun verifyAsync(jwtCryptoProvider: AsyncJWTCryptoProvider): VerificationResult<SDJwt> {
        return jwtCryptoProvider.verify(jwt).let {
            VerificationResult(
                sdJwt = this,
                signatureVerified = it.verified,
                disclosuresVerified = sdPayload.verifyDisclosures(),
                message = it.message
            )
        }
    }

    companion object {
        const val DIGESTS_KEY = "_sd"
        const val SEPARATOR = '~'
        const val SEPARATOR_STR = SEPARATOR.toString()
        const val SD_JWT_PATTERN =
            "^(?<sdjwt>(?<header>[A-Za-z0-9-_]+)\\.(?<body>[A-Za-z0-9-_]+)\\.(?<signature>[A-Za-z0-9-_]+))(?<disclosures>(~([A-Za-z0-9-_]+))+)?(~(?<kbjwt>([A-Za-z0-9-_]+)\\.([A-Za-z0-9-_]+)\\.([A-Za-z0-9-_]+))?)?\$"

        /**
         * Parse SD-JWT from a token string
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun parse(sdJwt: String): SDJwt {
            val matchResult = Regex(SD_JWT_PATTERN).matchEntire(sdJwt)
                ?: throw IllegalArgumentException("Invalid SD-JWT format: $sdJwt")
            val matchedGroups = matchResult.groups as MatchNamedGroupCollection
            val disclosures = matchedGroups["disclosures"]?.value?.trim(SEPARATOR)?.split(SEPARATOR)?.toSet() ?: setOf()

            return SDJwt(
                jwt = matchedGroups["sdjwt"]!!.value,
                header = Json.parseToJsonElement(
                    matchedGroups["header"]!!.value.decodeFromBase64Url().decodeToString()
                ).jsonObject,
                sdPayload = SDPayload.parse(
                    jwtBody = matchedGroups["body"]!!.value,
                    disclosures = disclosures
                ),
                keyBindingJwt = matchedGroups["kbjwt"]?.value?.let { KeyBindingJwt.parse(it) },
                isPresentation = matchedGroups["kbjwt"] != null || sdJwt.endsWith("~")
            )
        }

        /**
         * Parse SD-JWT from a token string and verify it
         * @return parsed SD-JWT, if token has been verified
         * @throws Exception if SD-JWT cannot be parsed
         */
        fun verifyAndParse(
            sdJwt: String,
            jwtCryptoProvider: JWTCryptoProvider
        ): VerificationResult<SDJwt> {
            return parse(sdJwt).verify(jwtCryptoProvider)
        }

        /**
         * Parse SD-JWT from a token string and verify it
         * @return parsed SD-JWT, if token has been verified
         * @throws Exception if SD-JWT cannot be parsed
         */
        @JsExport.Ignore
        suspend fun verifyAndParseAsync(
            sdJwt: String,
            jwtCryptoProvider: AsyncJWTCryptoProvider
        ): VerificationResult<SDJwt> {
            return parse(sdJwt).verifyAsync(jwtCryptoProvider)
        }

        fun createFromSignedJwt(
            signedJwt: String,
            sdPayload: SDPayload,
            withKBJwt: KeyBindingJwt? = null
        ): SDJwt {
            val sdJwt = parse(signedJwt)
            return SDJwt(
                jwt = sdJwt.jwt,
                header = sdJwt.header,
                sdPayload = sdPayload,
                keyBindingJwt = withKBJwt,
                isPresentation = sdJwt.isPresentation || withKBJwt != null
            )
        }

        /**
         * Sign the given payload as SD-JWT token, using the given JWT crypto provider, optionally with the specified key ID and holder binding
         * @param sdPayload Payload with selective disclosures to be signed
         * @param jwtCryptoProvider JWT Crypto Provider implementation that supports JWT creation on the target platform
         * @param keyID Optional key ID, if the crypto provider implementation requires it
         * @return  The signed SDJwt object
         */
        fun sign(
            sdPayload: SDPayload,
            jwtCryptoProvider: JWTCryptoProvider,
            keyID: String? = null,
            typ: String = "JWT",
            additionalHeaders: Map<String, Any> = mapOf()
        ): SDJwt = createFromSignedJwt(
            signedJwt = jwtCryptoProvider.sign(
                payload = sdPayload.undisclosedPayload,
                keyID = keyID,
                typ = typ,
                headers = additionalHeaders
            ),
            sdPayload = sdPayload
        )

        /**
         * Sign the given payload as SD-JWT token, using the given JWT crypto provider, optionally with the specified key ID and holder binding
         * @param sdPayload Payload with selective disclosures to be signed
         * @param jwtCryptoProvider JWT Crypto Provider implementation that supports JWT creation on the target platform
         * @param keyID Optional key ID, if the crypto provider implementation requires it
         * @param withKBJwt Optionally, append the given holder binding JWT to the signed SD-JWT token
         * @return  The signed SDJwt object
         */
        @JsExport.Ignore
        suspend fun signAsync(
            sdPayload: SDPayload,
            jwtCryptoProvider: AsyncJWTCryptoProvider,
            keyID: String? = null
        ): SDJwt = createFromSignedJwt(
            signedJwt = jwtCryptoProvider.sign(
                payload = sdPayload.undisclosedPayload,
                keyID = keyID
            ),
            sdPayload = sdPayload
        )

        /**
         * Check the given string, whether it matches the pattern of an SD-JWT
         */
        fun isSDJwt(value: String, sdOnly: Boolean = false): Boolean {
            return Regex(SD_JWT_PATTERN).matches(value) && (!sdOnly || value.contains("~"))
        }
    }
}
