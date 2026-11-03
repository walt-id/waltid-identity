package id.walt.ades.test

import id.walt.ades.crypto.AdesSigningCredentials
import id.walt.ades.x509.X509Certificate
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData

/** Public, synthetic test-only key and matching self-signed certificate; never use for real signatures. */
object CommonSigningFixture {
    val certificate = X509Certificate("308201ca30820171a00302010202144f868db2ef61211065a4f15efde855edc5d3cf4e300a06082a8648ce3d040302303b3121301f06035504030c185075626c696320416445532074657374206669787475726531163014060355040a0c0d77616c742e6964207465737473301e170d3236313130333230353035385a170d3336313033313230353035385a303b3121301f06035504030c185075626c696320416445532074657374206669787475726531163014060355040a0c0d77616c742e69642074657374733059301306072a8648ce3d020106082a8648ce3d030107034200040f68b589a7c3c52286e84f4c846a584616480255d4cb38608c205eae7d68cf421f1598886b2168ea8aa1f3b7021d58f8ff97ff35cb4fc97b6864bbfd60488698a3533051301d0603551d0e04160414ab44a302845d27ad87247347406575d21b81ce92301f0603551d23041830168014ab44a302845d27ad87247347406575d21b81ce92300f0603551d130101ff040530030101ff300a06082a8648ce3d0403020347003044022034ebe76397b95427920fc08f65d9c5e5664aafa58e2fd666651c6988116df24802202b1a5a196cb0773af51331d3c9489542580923bc7bc4bf32cdebfb573bf11f47".hexToByteArray())

    suspend fun credentials(): AdesSigningCredentials {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val key = runtime.restore(StoredKey.Software(
            StoredKey.CURRENT_VERSION, KeyId("public-ades-test"), certificate.publicKey.keySpec,
            setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            EncodedKey.Pkcs8Der(BinaryData("308187020100301306072a8648ce3d020106082a8648ce3d030107046d306b0201010420675905ce20d5c54e6720c3f9d71c9d0e383cbb874d7bad798d9618743d73f398a144034200040f68b589a7c3c52286e84f4c846a584616480255d4cb38608c205eae7d68cf421f1598886b2168ea8aa1f3b7021d58f8ff97ff35cb4fc97b6864bbfd60488698".hexToByteArray())),
        ))
        return AdesSigningCredentials(key, listOf(certificate))
    }
}
