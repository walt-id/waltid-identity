package id.walt.crypto.utils

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JweAlgorithm
import at.asitplus.signum.indispensable.josef.JweEncrypted
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.supreme.agree.keyAgreement
import at.asitplus.signum.supreme.os.IosKeychainProvider
import at.asitplus.signum.supreme.sign.Signer
import at.asitplus.signum.supreme.symmetric.decrypt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
actual object JweUtils {
    actual fun toJWE(
        payload: JsonObject,
        jwk: String,
        alg: String,
        enc: String,
        headerParams: Map<String, JsonElement>
    ): String {
        require(alg == "ECDH-ES") { "Algorithm $alg is not supported" }
        val recipientKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk)
            .toCryptoPublicKey().getOrThrow() as CryptoPublicKey.EC
        return kotlinx.coroutines.runBlocking {
            JweEncryptionSupreme.encryptEcdhEs(
                plaintext = payload.toString().encodeToByteArray(),
                recipientPublicKey = recipientKey,
                encAlg = enc,
            )
        }
    }

    actual fun parseJWE(
        jwe: String,
        jwk: String
    ): JwsUtils.JwsParts {
        val jweObj = JweEncrypted.deserialize(jwe).getOrThrow()
        val header = jweObj.header
        require(header.algorithm == JweAlgorithm.ECDH_ES) { "Algorithm ${header.algorithm} is not supported" }

        val epk = header.ephemeralKeyPair?.toCryptoPublicKey()?.getOrThrow() as CryptoPublicKey.EC

        val privateJwk = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk)
        val kid = privateJwk.keyId ?: error("JWK must have a kid for keychain lookup")

        val plaintext = kotlinx.coroutines.runBlocking {
            val signer = IosKeychainProvider.getSignerForKey(kid).getOrThrow()
            val z = (signer as Signer.ECDSA).keyAgreement(epk).getOrThrow()

            val encryption = header.encryption!!
            val keyLenBits = encryption.combinedEncryptionKeyLength.bits.toInt()
            val cekBytes = JweEncryptionSupreme.concatKdfPublic(z, keyLenBits, encryption.identifier)

            val key = keyFromIntermediate(encryption.algorithm, cekBytes)

            val aad = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
                .encode(jweObj.headerAsParsed).encodeToByteArray()
            key.decrypt(jweObj.iv, jweObj.ciphertext, jweObj.authTag, aad).getOrThrow()
        }

        val payloadJson = Json.parseToJsonElement(plaintext.decodeToString()).jsonObject

        return JwsUtils.JwsParts(
            header = Json.parseToJsonElement(
                joseCompliantSerializer.encodeToString(at.asitplus.signum.indispensable.josef.JweHeader.serializer(), header)
            ).jsonObject,
            payload = payloadJson,
            signature = ""
        )
    }
}
