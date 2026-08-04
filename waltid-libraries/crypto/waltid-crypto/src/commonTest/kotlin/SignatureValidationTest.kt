import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SignatureValidationTest {

    @Test
    fun shouldValidateSignatureOfGoogleCertificate() = runTest {
        val key = JWKKey.importPEM(publicKeyPem).getOrThrow()
        val certTbs = certTbsHex.hexToByteArray()
        val signature = signatureHex.hexToByteArray()
        key.verifyRaw(signature, certTbs).getOrThrow()
    }

    companion object {
        val publicKeyPem = """
            -----BEGIN PUBLIC KEY-----
            MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE83Rzp2iLYK5DuDXFgTB7S0md+8Fhzube
            Rr1r1WEYNa5A3XP3iZEwWus87oV8okB2O6nGuEfYKueSkWpz6bFyOZ8pn6KY019e
            WIZlD6GEZQbR3IvJx3PIjGov5cSr0R2K
            -----END PUBLIC KEY-----
        """.trimIndent()

        val certTbsHex = """
            30820225a00302010202107ff32d6b409d15d5965b05873a7c72e0300a06082a
            8648ce3d0403033047310b300906035504061302555331223020060355040a13
            19476f6f676c65205472757374205365727669636573204c4c43311430120603
            550403130b47545320526f6f74205234301e170d323331323133303930303030
            5a170d3239303232303134303030305a303b310b300906035504061302555331
            1e301c060355040a1315476f6f676c6520547275737420536572766963657331
            0c300a060355040313035745323059301306072a8648ce3d020106082a8648ce
            3d03010703420004357e1ff214ed907de19e2a344386c1d596e82770df9e04cb
            a9ca86790b084d468ac274a4bbd9bfeefd23d738f34bef5417e1bee7ca5525a8
            0c30ac2d5d4ea151a381fe3081fb300e0603551d0f0101ff040403020186301d
            0603551d250416301406082b0601050507030106082b06010505070302301206
            03551d130101ff040830060101ff020100301d0603551d0e0416041475bec477
            ae89f644377dcfb1681f1d1aebdc3459301f0603551d23041830168014804cd6
            eb74ff4936a3d5d8fcb53ec56af0941d8c303406082b06010505070101042830
            26302406082b060105050730028618687474703a2f2f692e706b692e676f6f67
            2f72342e637274302b0603551d1f042430223020a01ea01c861a687474703a2f
            2f632e706b692e676f6f672f722f72342e63726c30130603551d20040c300a30
            08060667810c010201
        """.replace(Regex("\\s"), "")

        val signatureHex = """
            306402300bbdb83655c835a3d2d97d3973d3f7f782b809d1816fe56445dbdeaa
            c00e45128fac93e81f60ec2e7e442c229491ecac02302fdf0c90764c2d6961d5
            4ffd98981884db34ea98ec9bcd8862ffd265e5336a9a0ced2349382f51bf91d0
            12a2c93838da
        """.replace(Regex("\\s"), "")
    }
}