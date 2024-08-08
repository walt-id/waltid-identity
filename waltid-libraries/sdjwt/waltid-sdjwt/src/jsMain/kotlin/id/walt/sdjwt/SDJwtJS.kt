package id.walt.sdjwt

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.js.Promise

@OptIn(ExperimentalJsExport::class)
@JsExport
class SDJwtJS(
    sdJwt: SDJwt,
) : SDJwt(sdJwt) {

    @JsName("disclosures")
    val disclosuresJS
        get() = sdPayload.sDisclosures.map { it.disclosure }.toTypedArray()

    @JsName("disclosureObjects")
    val disclosureObjectsJS
        get() = sdPayload.sDisclosures.map {
            JSON.parse<dynamic>(buildJsonObject {
                put("disclosure", it.disclosure)
                put("salt", it.salt)
                put("key", it.key)
                put("value", it.value)
            }.toString())
        }.toTypedArray()

    @JsName("undisclosedPayload")
    val undisclosedPayloadJS
        get() = JSON.parse<dynamic>(sdPayload.undisclosedPayload.toString())

    @JsName("fullPayload")
    val fullPayloadJS
        get() = JSON.parse<dynamic>(sdPayload.fullPayload.toString())

    @JsName("sdMap")
    val sdMapJS
        get() = JSON.parse<dynamic>(sdPayload.sdMap.toJSON().toString())

    @OptIn(DelicateCoroutinesApi::class)
    @JsName("verifyAsync")
    fun verifyAsyncJs(jwtCryptoProvider: JSAsyncJWTCryptoProvider): Promise<VerificationResult<SDJwtJS>> = GlobalScope.promise {
        verifyAsync(jwtCryptoProvider).let {
            VerificationResult(SDJwtJS(it.sdJwt), it.signatureVerified, it.disclosuresVerified, it.message)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun presentAllAsync(discloseAll: Boolean, withKBJwt: KeyBindingJwt? = null): Promise<SDJwtJS> = GlobalScope.promise {
        SDJwtJS(
            present(discloseAll, withKBJwt)
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun presentAsync(sdMap: dynamic, withKBJwt: KeyBindingJwt? = null): Promise<SDJwtJS> = GlobalScope.promise {
        SDJwtJS(
            present(SDMap.fromJSON(JSON.stringify(sdMap)), withKBJwt)
        )
    }

    override fun toString(formatForPresentation: Boolean, withKBJwt: Boolean): String {
        println("Formatting SD_JWT: ${disclosuresJS.joinToString(",")}")
        return listOf(jwt)
            .plus(disclosuresJS)
            .plus((if(withKBJwt) keyBindingJwt else null)?.let { listOf(it) }
                ?: (if (formatForPresentation) listOf("") else listOf()))
            .joinToString(SEPARATOR_STR)
    }

    companion object {
        @OptIn(DelicateCoroutinesApi::class)
        fun verifyAndParseAsync(sdJwt: String, jwtCryptoProvider: JSAsyncJWTCryptoProvider): Promise<VerificationResult<SDJwtJS>> =
            GlobalScope.promise {
                SDJwt.verifyAndParseAsync(sdJwt, jwtCryptoProvider).let {
                    VerificationResult(SDJwtJS(it.sdJwt), it.signatureVerified, it.disclosuresVerified, it.message)
                }
            }

        @OptIn(DelicateCoroutinesApi::class)
        fun signAsync(
            sdPayload: SDPayload,
            jwtCryptoProvider: JSAsyncJWTCryptoProvider,
            keyID: String? = null
        ): Promise<SDJwtJS> = GlobalScope.promise {
            SDJwtJS(
                SDJwt.signAsync(sdPayload, jwtCryptoProvider)
            )
        }
    }
}
