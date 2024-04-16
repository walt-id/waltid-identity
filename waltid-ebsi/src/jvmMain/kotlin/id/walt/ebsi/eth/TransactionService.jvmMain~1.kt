package id.walt.ebsi.eth

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.ECDSASignature
import org.web3j.crypto.*
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.util.*

actual object TransactionService {
  actual suspend fun signTransaction(
    key: Key,
    unsignedTransaction: UnsignedTransaction
  ): SignedTransaction {
    if(key.keyType != KeyType.secp256k1)
      throw IllegalArgumentException("Wrong key algorithm: secp256k1 is required.")
    val chainId = BigInteger(Numeric.hexStringToByteArray(unsignedTransaction.chainId))
    val rawTransaction = RawTransaction.createTransaction(
      BigInteger(Numeric.hexStringToByteArray(unsignedTransaction.nonce)),
      BigInteger(Numeric.hexStringToByteArray(unsignedTransaction.gasPrice)),
      BigInteger(Numeric.hexStringToByteArray(unsignedTransaction.gasLimit)),
      unsignedTransaction.to,
      BigInteger(Numeric.hexStringToByteArray(unsignedTransaction.value)),
      unsignedTransaction.data
    )

    var signatureData = Sign.SignatureData(chainId.toByteArray(), ByteArray(0), ByteArray(0))
    var rlpList = RlpList(TransactionEncoder.asRlpValues(rawTransaction, signatureData))

    val encodedTx = RlpEncoder.encode(rlpList)
    val sig = key.signECDSA(encodedTx)
    //        val sig = toECDSASignature(cs.sign(key.keyId, encodedTx), key.algorithm)
    val v = BigInteger
      .valueOf(getRecoveryId(key, encodedTx, sig).toLong())
      .add(chainId.multiply(BigInteger.valueOf(2)))
      .add(BigInteger.valueOf(35L))

    var sigR = sig.r
    if (sigR.size == 33) {
      sigR = Arrays.copyOfRange(sigR, 1, 33)
    }

    signatureData = Sign.SignatureData(v.toByteArray(), sigR, sig.s)
    rlpList = RlpList(TransactionEncoder.asRlpValues(rawTransaction, signatureData))

    return SignedTransaction(
      Numeric.toHexString(signatureData.r),
      Numeric.toHexString(signatureData.s),
      Numeric.toHexString(signatureData.v),
      Numeric.toHexString(RlpEncoder.encode(rlpList))
    )
  }

  private suspend fun getRecoveryId(key: Key, data: ByteArray, sig: ECDSASignature): Int {
    for (i in 0..3) {
      Sign.recoverFromSignature(i, ECDSASignature(BigInteger(sig.r), BigInteger(sig.s)), Hash.sha3(data))?.let {
        val address = Numeric.prependHexPrefix(Keys.toChecksumAddress(Utils.toEthereumAddress(key)))
        val recoveredAddress = Keys.toChecksumAddress(Numeric.prependHexPrefix(Keys.getAddress(it)))
        if (address == recoveredAddress) return i
      }
    }
    throw IllegalStateException("Could not construct a recoverable key. This should never happen.")
  }
}