package id.walt.certificate.x509

import id.walt.certificate.TestKeys
import id.walt.certificate.TestKeys.opensslHexFormat
import id.walt.certificate.x509.signum.SignumPublicKeyInfoUtil
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.toHexString
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicKeyInfoUtilTest {

    @Test
    fun shouldRetrieveKeyInfoOfRsaKey() = runTest {
        val key = JWKKey.importPEM(TestKeys.rsa4096privateKeyPem).getOrThrow()
        val info = SignumPublicKeyInfoUtil.publicKeyInfoOfKey(key)
        assertEquals("1.2.840.113549.1.1.1", info.algorithmOid)
        assertEquals("rsaEncryption", info.algorithmName)
        assertNull(info.ellipticCurveOid)
        assertEquals(526, info.keyValueRaw.size)
        assertEquals(
            "3082020a0282020100ccef761e1e9f10cfabef47bc4b27b6ff0b22e3138179e01f99dae91a70de149b713db2459dc5dfaae84f8c1bebd347bd5fcf2352c9fe8c10db4130a3116548f4123ca7ac3f54ebf4d956022cfe29b3b8c6760862d070a7a4c89b9b99579da85be222a3963ba720d8680de009ea7dc1f863de793ba56165a29d93f354e06b71fe580e0053a71e49e7a880d8c34362633880e45c0f9519a74dc3c589f27c3f93155f8573b0c30d588305a9a09f3f2817dba2044cf99060d4c24c55810e3e8540276ee6b609eab2ed734f24a60b1c35b561e392214c749ecf389e8fdc78201e8315850c0e931edbd28c4176cd0595a538263e73f0ae5cf5050e01096e8d94fc60d3cacc0b483eb57acabb7a6a888492ea667639b2df60bea3432e3cabd8074645fe9e8776964dce97dbd598cfcb5708ca8c6fabccadbdfe41de55f13cf0683f700362c5baac5bb5565941bd655da1587aa65b3b468443dd172c451a69afa08fdbb147ce510b5dc8fbab02a58dae10800b6f615a0b5955ec0219709f2b85b5977be353860ef1023ce15ba76a99515497cca216e89c3606167c361d88f9ac4fc4b6ae9f3dbd287a04eadc82ef385ee58b69477e425c10c2a2b2b01747ef75560942172084afd8724bccb0f77387dc300245a1c7a925d05708912bba977c0d925772c164e722508241059b27ad9e96cbfbe70746e8ea1a64f121141b1bd18f4d5a8eb70203010001",
            info.keyValueHex
        )
        assertEquals(
            "6B:7E:AF:3C:1F:69:43:FA:0D:A0:85:4B:D0:B7:7D:20:9F:73:B2:47", //value taken from openssl created certificate
            info.keyId.toHexString(opensslHexFormat)
        )
    }


    @Ignore //loading of ed25519 pem is not supported by com.nimbusds.jose
    @Test
    fun shouldRetrieveKeyInfoOfEd25519Key() = runTest {
        val key = JWKKey.importPEM(TestKeys.ed25519KeyPem).getOrThrow()
        val info = SignumPublicKeyInfoUtil.publicKeyInfoOfKey(key)
        assertEquals("1.3.101.112", info.algorithmOid)
        assertEquals("id-Ed25519", info.algorithmName)
        assertEquals(
            "48:B3:7C:31:23:20:88:0C:EC:40:04:36:25:89:EF:BD:8B:90:9B:0F",
            info.keyId.toHexString(opensslHexFormat)
        )
    }

    @Test
    fun shouldRetrieveKeyInfoOfEcKey() = runTest {
        val key = JWKKey.importPEM(TestKeys.ecP256KeyPem).getOrThrow()
        val info = SignumPublicKeyInfoUtil.publicKeyInfoOfKey(key)
        assertEquals("1.2.840.10045.2.1", info.algorithmOid)
        assertEquals("id-ecPublicKey", info.algorithmName)
        assertEquals("1.2.840.10045.3.1.7", info.ellipticCurveOid)
        assertEquals(
            "A7:97:E8:20:1F:A9:32:29:98:9D:AE:73:2D:3A:A6:EC:BA:E5:81:09", //value taken from openssl created certificate
            info.keyId.toHexString(opensslHexFormat)
        )
    }
}