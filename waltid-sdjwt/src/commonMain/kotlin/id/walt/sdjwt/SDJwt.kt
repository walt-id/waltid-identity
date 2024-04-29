package id.walt.sdjwt

import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * SD-JWT object, providing signed JWT token, header and payload with disclosures, as well as optional holder binding
 */
@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
open class SDJwt internal constructor(
    val jwt: String,
    protected val header: JsonObject,
    protected val sdPayload: SDPayload,
    val holderJwt: String? = null,
    protected val isPresentation: Boolean = false
) {
    internal constructor(sdJwt: SDJwt) : this(sdJwt.jwt, sdJwt.header, sdJwt.sdPayload, sdJwt.holderJwt, sdJwt.isPresentation)

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
     * Signature key ID from JWT header, if present
     */
    val keyID
        get() = header["kid"]?.jsonPrimitive?.contentOrNull

    /**
     * Signature key in JWK format, from JWT header, if present
     */
    val jwk
        get() = header["jwk"]?.jsonPrimitive?.contentOrNull

    override fun toString() = toString(isPresentation)

    @JsName("toFormattedString")
    open fun toString(formatForPresentation: Boolean): String {
        return listOf(jwt)
            .plus(disclosures)
            .plus(holderJwt?.let { listOf(it) } ?: (if (formatForPresentation) listOf("") else listOf()))
            .joinToString(SEPARATOR_STR)
    }

    /**
     * Present SD-JWT with selection of disclosures
     * @param sdMap Selective disclosure map, indicating for each field (recursively) whether it should be disclosed or undisclosed in the presentation
     * @param withHolderJwt Optionally, adds the provided JWT as holder binding to the presented SD-JWT token
     */
    @JsName("present")
    fun present(sdMap: SDMap?, withHolderJwt: String? = null): SDJwt {
        return SDJwt(
            jwt,
            header,
            sdMap?.let { sdPayload.withSelectiveDisclosures(it) } ?: sdPayload.withoutDisclosures(),
            withHolderJwt ?: holderJwt, isPresentation = true)
    }

    /**
     * Shortcut to presenting the SD-JWT, with all disclosures selected or unselected
     * @param discloseAll true: disclose all selective disclosures, false: all selective disclosures remain undisclosed
     * @param withHolderJwt Optionally, adds the provided JWT as holder binding to the presented SD-JWT token
     */
    @JsName("presentAll")
    fun present(discloseAll: Boolean, withHolderJwt: String? = null): SDJwt {
        return SDJwt(
            jwt,
            header,
            if (discloseAll) {
                sdPayload
            } else {
                sdPayload.withoutDisclosures()
            },
            withHolderJwt ?: holderJwt, isPresentation = true
        )
    }

    /**
     * Verify the SD-JWT by checking the signature, using the given JWT crypto provider, and matching the disclosures against the digests in the JWT payload
     * @param jwtCryptoProvider JWT crypto provider, that implements standard JWT token verification on the target platform
     */
    fun verify(jwtCryptoProvider: JWTCryptoProvider): VerificationResult<SDJwt> {
        return jwtCryptoProvider.verify(jwt).let {
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
     * @param jwtCryptoProvider JWT crypto provider, that implements standard JWT token verification on the target platform
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
            "^(?<sdjwt>(?<header>[A-Za-z0-9-_]+)\\.(?<body>[A-Za-z0-9-_]+)\\.(?<signature>[A-Za-z0-9-_]+))(?<disclosures>(~([A-Za-z0-9-_]+))+)?(~(?<holderjwt>([A-Za-z0-9-_]+)\\.([A-Za-z0-9-_]+)\\.([A-Za-z0-9-_]+))?)?\$"

        /**
         * Parse SD-JWT from a token string
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun parse(sdJwt: String): SDJwt {
            val matchResult = Regex(SD_JWT_PATTERN).matchEntire(sdJwt) ?: throw IllegalArgumentException("Invalid SD-JWT format: $sdJwt")
            val matchedGroups = matchResult.groups as MatchNamedGroupCollection
            val disclosures = matchedGroups["disclosures"]?.value?.trim(SEPARATOR)?.split(SEPARATOR)?.toSet() ?: setOf()
            return SDJwt(
                matchedGroups["sdjwt"]!!.value,
                Json.parseToJsonElement(Base64.UrlSafe.decode(matchedGroups["header"]!!.value).decodeToString()).jsonObject,
                SDPayload.parse(
                    matchedGroups["body"]!!.value,
                    disclosures
                ),
                matchedGroups["holderjwt"]?.value
            )
        }

        /**
         * Parse SD-JWT from a token string and verify it
         * @return parsed SD-JWT, if token has been verified
         * @throws Exception if SD-JWT cannot be parsed
         */
        fun verifyAndParse(sdJwt: String, jwtCryptoProvider: JWTCryptoProvider): VerificationResult<SDJwt> {
            return parse(sdJwt).verify(jwtCryptoProvider)
        }

        /**
         * Parse SD-JWT from a token string and verify it
         * @return parsed SD-JWT, if token has been verified
         * @throws Exception if SD-JWT cannot be parsed
         */
        @JsExport.Ignore
        suspend fun verifyAndParseAsync(sdJwt: String, jwtCryptoProvider: AsyncJWTCryptoProvider): VerificationResult<SDJwt> {
            return parse(sdJwt).verifyAsync(jwtCryptoProvider)
        }

        fun createFromSignedJwt(signedJwt: String, sdPayload: SDPayload, withHolderJwt: String? = null): SDJwt {
            val sdJwt = parse(signedJwt)
            return SDJwt(
                jwt = sdJwt.jwt,
                header = sdJwt.header,
                sdPayload = sdPayload,
                holderJwt = withHolderJwt
            )
        }

        /**
         * Sign the given payload as SD-JWT token, using the given JWT crypto provider, optionally with the specified key ID and holder binding
         * @param sdPayload Payload with selective disclosures to be signed
         * @param jwtCryptoProvider Crypto provider implementation, that supports JWT creation on the target platform
         * @param keyID Optional key ID, if the crypto provider implementation requires it
         * @param withHolderJwt Optionally, append the given holder binding JWT to the signed SD-JWT token
         * @return  The signed SDJwt object
         */
        fun sign(
            sdPayload: SDPayload,
            jwtCryptoProvider: JWTCryptoProvider,
            keyID: String? = null,
            withHolderJwt: String? = null,
            typ: String = "JWT"
        ): SDJwt = createFromSignedJwt(
            jwtCryptoProvider.sign(sdPayload.undisclosedPayload, keyID, typ), sdPayload, withHolderJwt
        )

        /**
         * Sign the given payload as SD-JWT token, using the given JWT crypto provider, optionally with the specified key ID and holder binding
         * @param sdPayload Payload with selective disclosures to be signed
         * @param jwtCryptoProvider Crypto provider implementation, that supports JWT creation on the target platform
         * @param keyID Optional key ID, if the crypto provider implementation requires it
         * @param withHolderJwt Optionally, append the given holder binding JWT to the signed SD-JWT token
         * @return  The signed SDJwt object
         */
        @JsExport.Ignore
        suspend fun signAsync(
            sdPayload: SDPayload,
            jwtCryptoProvider: AsyncJWTCryptoProvider,
            keyID: String? = null,
            withHolderJwt: String? = null
        ): SDJwt = createFromSignedJwt(
            jwtCryptoProvider.sign(sdPayload.undisclosedPayload, keyID), sdPayload, withHolderJwt
        )

        /**
         * Check the given string, whether it matches the pattern of an SD-JWT
         */
        fun isSDJwt(value: String): Boolean {
            return Regex(SD_JWT_PATTERN).matches(value)
        }
    }
}
