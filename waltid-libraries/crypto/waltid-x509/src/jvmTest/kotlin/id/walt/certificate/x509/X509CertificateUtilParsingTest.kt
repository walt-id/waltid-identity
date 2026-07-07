package id.walt.certificate.x509

import id.walt.certificate.TestData.GOOGLE_CERTIFICATE_PEM
import id.walt.certificate.TestData.V_TRUST_ROOT_CA_CERTIFICATE_PEM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class X509CertificateUtilParsingTest {

    @Test
    fun shouldParseCertificate() {
        X509CertificateUtil.parseCertificatePem(GOOGLE_CERTIFICATE_PEM).also { cert ->
            assertEquals(3, cert.data.version)
            assertEquals("4bfc99602f79066f12ca86719fe05960", cert.data.serialNumberHex)
            assertEquals("CN=*.google.com", cert.data.subjectDn)
            assertEquals("C=US,O=Google Trust Services,CN=WE2", cert.data.issuerDn)
            assertEquals("2026-06-15T08:39:06Z", cert.data.validity.notBefore.toString())
            assertEquals("2026-09-07T08:39:05Z", cert.data.validity.notAfter.toString())
            cert.data.subjectPublicKeyInfo.also {
                assertEquals("id-ecPublicKey", it.algorithmName)
                assertEquals("1.2.840.10045.2.1", it.algorithmOid)
                assertEquals("1.2.840.10045.3.1.7", it.ellipticCurveOid)
                assertEquals(
                    "04193cba5a1b30b9d743d6b520d6080823306ee00276b884a8aa889701f7930c9b86f4617ae6b95ac7a0938e5bfe2b3a84be1e8d77af19582390e163b9098238e9",
                    it.publicKeyHex
                )
            }
            assertEquals("ecdsa-with-SHA256", cert.signatureAlgorithmName)
            assertEquals("1.2.840.10045.4.3.2", cert.signatureAlgorithmOid)
            assertEquals("da3a2976598e7edd7d23cdb891de441c24450f8313807f82d18df3979a3799ed", cert.fingerprintSha256Hex)
            assertEquals(
                "3045022041321f429ec542fd90aafcbac898dc085652f725142b221529b29345693d21be022100b26f6d08392ed3569b58a132aeb56612e2e964d422ceb4d5ea02f7a00e5674c9",
                cert.signatureValueHex
            )
        }
    }

    @Test
    fun shouldParseVtrust() {
        X509CertificateUtil.parseCertificatePem(V_TRUST_ROOT_CA_CERTIFICATE_PEM).also { cert ->
            assertEquals("sha256WithRSAEncryption", cert.signatureAlgorithmName)
            assertEquals("1.2.840.113549.1.1.11", cert.signatureAlgorithmOid)
            cert.data.subjectPublicKeyInfo.also {
                assertNull(it.ellipticCurveOid)
                assertEquals(
                    "3082020a0282020100bd557c61d3b81d046205a0ae6cb770b441ea4b035e103f905a1c8b3bb0668b6c48a61c22bad54092ee33b22359c98ebc58da8b9ed019f22f59c68c635aba9fa30bb0b39a5cba11b812e90cbbcf6e6c80872914032c8d249ac86483b56aac132c33f19fdc2c613c1a3f70559bad00527fcf04b9fe36fa9cc016ae62fe964c437e5514be1ab3d26dc2af7666956b2ab09477855e040f621d6375f76be7cb5b9a70ec3e6705f0fe070880cf28db05c614272f867df027deffe67e3348e70b1e58d1272b530e574a65d7fba28060fc4cbc3553016a977282aff11d70e89cf5ef5ec26cc7477e5a9485264d3bbaeb4ce8b009c265c29d9d099b4eb59705acf506a0f736057ef490b26bc4b4f964eae91a0ac80da8ed27c9d4e7b3b9ab822290273d2ae87c90efbc4ffde20a24a7de6524a45deac07630d37750f80d049b94360173ca0658a6d33bdcfa044613558ac94447b851391a2ee834e279cb594a0a7fbca6ef1f03676a592b256293d95319663c276229864da46beeffd44ebad5b4e28e485a001909f105d9ce91b1f7ebe9394ff66f04439a55f53e0514bdbfb359b4d88e3384a39052aab3029560f90c4c68f9eed5170df87157b525e429ee655dafd1ee3c170b5a43c5a586ea249ee20507dc34421291d63974ae4c4182dbf2a648d1b39bf333aaf3a6c0c54ef5f49d7663e602c6224bc1953f50642c54e5b6f03c29cf570203010001",
                    it.publicKeyHex
                )
            }
            assertEquals(
                "8a71de6559336f426c26e53880d00d88a18da4c6a91f0dcb6194e206c5c96387",
                cert.fingerprintSha256Hex
            )
        }
    }
}