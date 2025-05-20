package id.walt.cli.crypto

import id.walt.cli.io.File
import id.walt.cli.io.Files
import id.walt.cli.util.getResourcePath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CryptoUtilsTest {

    @Test
    fun testDecryptKeySuccess() {

        val encryptedKey = getResourcePath(this, "key/rsa_by_openssl_encrypted_pvt_key.pem")
        val decryptedKey = getResourcePath(this, "key/rsa_by_openssl_decrypted_pvt_key.pem")

        // generate private key
        // openssl genpkey -algorithm RSA -out /tmp/private_key_enc.pem -pkeyopt rsa_keygen_bits:2048

        // encrypt
        // openssl pkcs8 -topk8 -in /tmp/private_key.pem -out /tmp/encrypted_key.pem -v2 aes-256-cbc -iter 10000

        // decrypt
        // openssl pkcs8 -in  /tmp/encrypted_key.pem -out /tmp/decrypted_key.pem -passin pass:123123

        val result = CryptoUtils.decryptKey(File(encryptedKey), "123123")
        val decrypted = File(decryptedKey).readText().trim()
        assertEquals(decrypted, result)
    }

    @Test
    fun testDecryptKeyInvalidInput() {
        val tmpFile = createTempFileWithContent("Invalid content")
        assertThrows<IllegalArgumentException> {
            CryptoUtils.decryptKey(File(tmpFile.absolutePath), "passphrase")
        }
    }

    private fun createTempFileWithContent(content: String): File {
        val tmpFile = Files.createTempFile("temp-file", ".tmp")
//        tmpFile.deleteOnExit()
//        val bw = BufferedWriter(java.io.FileWriter(tmpFile))
//        bw.write(content)
//        bw.close()
        tmpFile.writeText(content)
        return tmpFile
    }
}