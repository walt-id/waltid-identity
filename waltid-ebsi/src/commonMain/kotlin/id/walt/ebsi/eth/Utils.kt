package id.walt.ebsi.eth

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.ebsi.rpc.EbsiRpcMethod
import id.walt.ebsi.rpc.InsertDidDocumentParams
import id.walt.ebsi.rpc.JsonRpcRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import kotlinx.serialization.json.*
import org.komputing.khash.keccak.Keccak
import org.komputing.khash.keccak.KeccakParameter

@OptIn(ExperimentalStdlibApi::class)
object Utils {

  // compute ethereum address from public key
  // https://www.rareskills.io/post/generate-ethereum-address-from-private-key-python
  // "An ethereum address is the last 20 bytes of the keccak256 of the public key. The public key algorithm is secp256k1 [...] The public key is the concatenation of x and y, and that is what we take the hash of."
  // getPublicKeyRepresentation() seems to return some other bytes first and then the 64 bytes of the concatenation of the x and y coordinates
  suspend fun toEthereumAddress(key: Key) = key.getPublicKeyRepresentation().takeLast(64).toByteArray().let {
    Keccak.digest(it, KeccakParameter.KECCAK_256)
  }.takeLast(20).toByteArray().toHexString().let { "0x$it" }
}