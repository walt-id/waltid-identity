package id.walt.did.utils

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.document.DidEbsiDocument
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import kotlinx.serialization.json.*
import org.komputing.khash.keccak.Keccak
import org.komputing.khash.keccak.KeccakParameter

@OptIn(ExperimentalStdlibApi::class)
object EbsiRPCUtils {
  // compute ethereum address from public key
  // https://www.rareskills.io/post/generate-ethereum-address-from-private-key-python
  // "An ethereum address is the last 20 bytes of the keccak256 of the public key. The public key algorithm is secp256k1 [...] The public key is the concatenation of x and y, and that is what we take the hash of."
  // getPublicKeyRepresentation() seems to return some other bytes first and then the 64 bytes of the concatenation of the x and y coordinates
  suspend fun toEthereumAddress(key: Key) = key.getPublicKeyRepresentation().takeLast(64).toByteArray().let {
    Keccak.digest(it, KeccakParameter.KECCAK_256)
  }.takeLast(20).toByteArray().toHexString().let { "0x$it" }

  suspend fun generateInsertDidDocumentPayload(
    did: String, secp256k1Key: Key,
    notBefore: Instant = Clock.System.now(), notAfter: Instant = notBefore.plus(365*24, DateTimeUnit.HOUR)
  ) = buildJsonObject {
    put("jsonrpc", "2.0")
    put("method", "insertDidDocument")
    put("params", buildJsonArray {
      add(buildJsonObject {
        put("from", toEthereumAddress(secp256k1Key))
        put("did", did)
        put("baseDocument", DidEbsiDocument(
          id = did, verificationMethod = listOf(
            DidEbsiDocument.VerificationMethod(
              id = "$did#${secp256k1Key.getKeyId()}",
              type = secp256k1Key.keyType.name,
              controller = did,
              publicKeyJwk = secp256k1Key.exportJWKObject()
            )
          ),
          assertionMethod = null,
          authentication = listOf("$did#${secp256k1Key.getKeyId()}"),
          capabilityInvocation = listOf("$did#${secp256k1Key.getKeyId()}"),
          capabilityDelegation = null, keyAgreement = null
        ).let { Json.encodeToJsonElement(it) })
        put("vMethodId", secp256k1Key.getThumbprint())
        put("publicKey", secp256k1Key.getPublicKeyRepresentation().toHexString().let { "0x04$it" })
        put("isSecp256k1", (secp256k1Key.keyType == KeyType.secp256k1).also {
          if(!it) throw IllegalArgumentException("Key for inserting Did document must have type Secp256k1")
        })
        put("notBefore", notBefore.epochSeconds)
        put("notAfter", notAfter.epochSeconds)
      })
    })
  }
}