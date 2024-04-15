package id.walt.cli

import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PemImportTests {

    private val publicSecp256k1Key = """
        -----BEGIN PUBLIC KEY-----
        MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAED7KA0wd4qOX37HvlneDt1XdfV8fVRG2x
        WQGCjsO8s0fxZ09kNE4bMKQoDfpRSnplCBJ93SnPHUFSJQu5CeFMew==
        -----END PUBLIC KEY-----
    """.trimIndent()

    private val privateSecp256k1Key = """
        -----BEGIN PRIVATE KEY-----
        MIGEAgEAMBAGByqGSM49AgEGBSuBBAAKBG0wawIBAQQghItf3kprlQm9bYmnDKch
        RxBRCWaQBhKi+b2sSjCxCKmhRANCAAQPsoDTB3io5ffse+Wd4O3Vd19Xx9VEbbFZ
        AYKOw7yzR/FnT2Q0ThswpCgN+lFKemUIEn3dKc8dQVIlC7kJ4Ux7
        -----END PRIVATE KEY-----
        -----BEGIN PUBLIC KEY-----
        MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAED7KA0wd4qOX37HvlneDt1XdfV8fVRG2x
        WQGCjsO8s0fxZ09kNE4bMKQoDfpRSnplCBJ93SnPHUFSJQu5CeFMew==
        -----END PUBLIC KEY-----
    """.trimIndent()

    @Test
    fun testImportPublicSecp256k1KeyPem() = runTest {
        val imported = JWKKey.importPEM(publicSecp256k1Key)
        println(imported.getOrThrow())
    }

    @Test
    fun testImportPrivateSecp256k1KeyPem() = runTest {
        val imported = JWKKey.importPEM(privateSecp256k1Key)
        println(imported.getOrThrow())
    }

}
