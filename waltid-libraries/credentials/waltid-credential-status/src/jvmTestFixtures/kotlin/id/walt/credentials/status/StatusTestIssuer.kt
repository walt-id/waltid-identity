package id.walt.credentials.status

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.did.dids.DidService
import id.walt.statuslist.codec.TokenStatusListCodec
import id.walt.statuslist.codec.BitstringStatusListCodec
import id.walt.statuslist.codec.StatusList2021Codec
import id.walt.statuslist.model.IndexedStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Real signatures for protocol-to-wallet adapter tests, shared by those distinct boundaries. */
class StatusTestIssuer private constructor(val key: JWKKey, val did: String, val keyId: String) {
    val uri = "https://status.example/list"

    companion object {
        suspend fun create(): StatusTestIssuer {
            DidService.minimalInit()
            val key = JWKKey.generate(KeyType.Ed25519)
            val did = DidService.registerByKey("key", key).did
            val keyId = DidService.resolveToCrypto2Keys(did).getOrThrow().single().id.value
            return StatusTestIssuer(key, did, keyId)
        }
    }

    suspend fun credential(index: Int = 0, expiry: Long = 4_000_000_000, validFrom: Long? = null): DigitalCredential =
        CredentialParser.detectAndParse(sign(buildJsonObject {
            put("iss", did); put("sub", "urn:example:holder"); put("exp", expiry)
            validFrom?.let { put("nbf", it) }
            putJsonObject("vc") {
                putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                putJsonArray("type") { add("VerifiableCredential") }
                put("issuer", did)
                putJsonObject("credentialSubject") { put("id", "urn:example:holder") }
                putJsonObject("status") {
                    putJsonObject("status_list") { put("idx", index); put("uri", uri) }
                }
            }
        })).second

    suspend fun list(value: UInt = 0u, ttl: Long = 30, expiry: Long = 4_000_000_000, bits: Int = 1): String = sign(buildJsonObject {
        put("iss", did); put("sub", uri); put("iat", 1_700_000_000); put("exp", expiry); put("ttl", ttl)
        putJsonObject("status_list") {
            put("bits", bits)
            put("lst", TokenStatusListCodec.encode(listOf(IndexedStatus(0, value), IndexedStatus(1, value)), bits, 16))
        }
    }, "statuslist+jwt")

    suspend fun w3cCredential(includeUnsupported: Boolean = false, size: Int = 1, purpose: String = "revocation",
        index: JsonPrimitive = JsonPrimitive("0"), reference: JsonElement? = null,
        legacy: Boolean = false, extraEntries: List<JsonElement> = emptyList()): DigitalCredential =
        CredentialParser.detectAndParse(sign(buildJsonObject {
            put("iss", did); put("exp", 4_000_000_000)
            putJsonObject("vc") {
                putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                putJsonArray("type") { add("VerifiableCredential") }
                put("issuer", did)
                putJsonObject("credentialSubject") { put("id", "urn:example:holder") }
                putJsonArray("credentialStatus") {
                    add(buildJsonObject {
                        put("id", "$uri#0"); put("type", if (legacy) "StatusList2021Entry" else "BitstringStatusListEntry"); put("statusPurpose", purpose)
                        put("statusListIndex", index); put("statusListCredential", uri)
                        if (size != 1) {
                            put("statusSize", size)
                            putJsonArray("statusMessage") { repeat(1.shl(size)) { add(buildJsonObject {
                                put("status", "0x${it.toString(16)}"); put("message", "Debug status $it")
                            }) } }
                        }
                        reference?.let { put("statusReference", it) }
                    })
                    if (includeUnsupported) add(buildJsonObject { put("type", "UnknownStatusMethod") })
                    extraEntries.forEach { add(it) }
                }
            }
        })).second

    suspend fun w3cList(value: UInt = 0u, purpose: String = "revocation", id: String? = uri, bytes: Int = 16_384,
        size: Int = 1, purposes: List<String> = listOf(purpose), validUntil: String? = null,
        declaredSize: Int? = size.takeUnless { it == 1 }, legacy: Boolean = false): String =
        sign(buildJsonObject {
            put("iss", did); put("exp", 4_000_000_000)
            putJsonObject("vc") {
                id?.let { put("id", it) }; put("issuer", did)
                validUntil?.let { put("validUntil", it) }
                putJsonArray("type") { add("VerifiableCredential"); add(if (legacy) "StatusList2021Credential" else "BitstringStatusListCredential") }
                putJsonObject("credentialSubject") {
                    put("id", "$uri#list"); put("type", if (legacy) "StatusList2021" else "BitstringStatusList")
                    if (purposes.size == 1) put("statusPurpose", purposes.single())
                    else putJsonArray("statusPurpose") { purposes.forEach { add(it) } }
                    declaredSize?.let { put("statusSize", it) }
                    put("ttl", 60_000)
                    put("encodedList", (if (legacy) StatusList2021Codec else BitstringStatusListCodec).encode(listOf(IndexedStatus(0, value)), size, bytes))
                }
            }
        }, "vc+jwt")

    suspend fun sign(payload: JsonObject, type: String = "JWT"): String {
        val header = buildJsonObject { put("alg", "EdDSA"); put("kid", keyId); put("typ", type) }
        val data = header.toString().encodeToByteArray().encodeToBase64Url() + "." +
            payload.toString().encodeToByteArray().encodeToBase64Url()
        return data + "." + key.signRaw(data.encodeToByteArray()).encodeToBase64Url()
    }
}
