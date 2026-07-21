package id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension
import id.walt.x509.id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory
import kotlinx.io.bytestring.hexToByteString
import kotlinx.io.bytestring.toHexString
import org.bouncycastle.asn1.DERBitString
import kotlin.test.Test
import kotlin.test.assertEquals

class BouncySubjectKeyIdentifierExtensionTest {

    @Test
    fun shouldCreateKeyIdentifier() {
        val publicKeyHexString =
            "3082020a0282020100bd557c61d3b81d046205a0ae6cb770b441ea4b035e103f905a1c8b3bb0668b6c48a61c22bad54092ee33b22359c98ebc58da8b9ed019f22f59c68c635aba9fa30bb0b39a5cba11b812e90cbbcf6e6c80872914032c8d249ac86483b56aac132c33f19fdc2c613c1a3f70559bad00527fcf04b9fe36fa9cc016ae62fe964c437e5514be1ab3d26dc2af7666956b2ab09477855e040f621d6375f76be7cb5b9a70ec3e6705f0fe070880cf28db05c614272f867df027deffe67e3348e70b1e58d1272b530e574a65d7fba28060fc4cbc3553016a977282aff11d70e89cf5ef5ec26cc7477e5a9485264d3bbaeb4ce8b009c265c29d9d099b4eb59705acf506a0f736057ef490b26bc4b4f964eae91a0ac80da8ed27c9d4e7b3b9ab822290273d2ae87c90efbc4ffde20a24a7de6524a45deac07630d37750f80d049b94360173ca0658a6d33bdcfa044613558ac94447b851391a2ee834e279cb594a0a7fbca6ef1f03676a592b256293d95319663c276229864da46beeffd44ebad5b4e28e485a001909f105d9ce91b1f7ebe9394ff66f04439a55f53e0514bdbfb359b4d88e3384a39052aab3029560f90c4c68f9eed5170df87157b525e429ee655dafd1ee3c170b5a43c5a586ea249ee20507dc34421291d63974ae4c4182dbf2a648d1b39bf333aaf3a6c0c54ef5f49d7663e602c6224bc1953f50642c54e5b6f03c29cf570203010001"
        val publicKey = publicKeyHexString.hexToByteString()


        val ext = BouncyExtensionFactory.createSubjectKeyIdentifierExtension(
            SubjectKeyIdentifierExtension.Builder(),
            DERBitString(publicKey.toByteArray())
        )

        val parsed = BouncyExtensionFactory.parseExtension(ext) as SubjectKeyIdentifierExtension
        assertEquals("54627063f1758443588ed11620b1c6ac1abcf689", parsed.keyIdentifier.toHexString())
    }
}