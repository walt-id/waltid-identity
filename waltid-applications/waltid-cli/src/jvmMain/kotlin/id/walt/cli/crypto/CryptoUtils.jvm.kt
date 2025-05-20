package id.walt.cli.crypto


import id.walt.cli.io.File
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMEncryptedKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.bc.BcPEMDecryptorProvider
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import org.bouncycastle.pkcs.PKCSException
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder
import java.io.FileReader
import java.security.Security
import java.util.*

actual object CryptoUtils {

    init {
        Security.addProvider(BouncyCastleProvider()) // TODO: do not add at every function call
    }

    actual fun decryptKey(input: File, passphrase: String): String {

        try {
            val pemParser = PEMParser(FileReader(input.nativeFile))
            val pemObject = runCatching {
//                try {
                val pemObj = pemParser.readObject()
                pemObj ?: error("No object in PEM")
//                } catch (e: Exception) {
//                    print(e)
//                }

            }.getOrElse { throw IllegalArgumentException("Could not parse object from PEM", it) }
            val pki = when (pemObject) {
                is PKCS8EncryptedPrivateKeyInfo -> {
                    val decryptorProviderBuilder = JcePKCSPBEInputDecryptorProviderBuilder().setProvider("BC")
                    val inputDecryptorProvider = decryptorProviderBuilder.build(passphrase.toCharArray())

                    pemObject.decryptPrivateKeyInfo(inputDecryptorProvider)
                }

                is PEMEncryptedKeyPair -> {
                    val pkp = pemObject.decryptKeyPair(BcPEMDecryptorProvider(passphrase.toCharArray()))

                    pkp.privateKeyInfo
                }

                else -> throw PKCSException("Invalid encrypted private key class: " + pemObject::class.qualifiedName)
            }

            val converter = JcaPEMKeyConverter().setProvider("BC")

            val encodedKey = Base64.getEncoder().encodeToString(converter.getPrivateKey(pki).encoded)
            val formattedAsPem = encodedKey.chunked(64).joinToString("\n")

            return "-----BEGIN PRIVATE KEY-----\n$formattedAsPem\n-----END PRIVATE KEY-----"

        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw Exception("Failed to load key from $input: $e")
        }
    }
}