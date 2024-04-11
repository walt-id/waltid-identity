package id.walt.ebsi.eth

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.ECDSASignature

actual object TransactionService {
  actual suspend fun signTransaction(
    key: Key,
    unsignedTransaction: UnsignedTransaction
  ): SignedTransaction {
    TODO("Not yet implemented")
  }

  actual fun getRecoveryId(
    keyAlias: String,
    data: ByteArray,
    sig: ECDSASignature
  ): Int {
    TODO("Not yet implemented")
  }
}