package id.walt.crypto2.pkcs11

import id.walt.crypto2.algorithms.KeyWrappingAlgorithm

object Pkcs11WrappingAlgorithms {
    val RSA_PKCS1 = KeyWrappingAlgorithm.BuiltIn("RSA-PKCS1-v1_5")
}
