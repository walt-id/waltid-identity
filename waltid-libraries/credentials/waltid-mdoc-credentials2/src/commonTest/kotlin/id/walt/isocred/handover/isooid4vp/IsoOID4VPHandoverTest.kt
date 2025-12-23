@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.isocred.handover.isooid4vp

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.handover.isooid4vp.IsoOID4VPHandover
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class IsoOID4VPHandoverTest {

    private val specExampleOID4VPHandoverCBORHex = """
        835820DA25C527E5FB75BC2DD31267C02237C4462BA0C1BF37071F692E7DD93B10AD0B5820F6ED8E3220D
        3C59A5F17EB45F48AB70AEECF9EE21744B1014982350BD96AC0C572616263646566676831323334353637
        383930
    """.trimIndent().lines().joinToString("")

    private val specExampleSessionTranscriptCBORHex = """
        83F6F6835820DA25C527E5FB75BC2DD31267C02237C4462BA0C1BF37071F692E7DD93B10AD0B5820F6ED8
        E3220D3C59A5F17EB45F48AB70AEECF9EE21744B1014982350BD96AC0C572616263646566676831323334
        353637383930
    """.trimIndent().lines().joinToString("")

    @Test
    fun testIsoOid4VPHandoverSerialization() {
        val decoded = coseCompliantCbor.decodeFromHexString<IsoOID4VPHandover>(specExampleOID4VPHandoverCBORHex)
        println(decoded)

        val out = coseCompliantCbor.encodeToHexString(decoded).uppercase()
        assertEquals(specExampleOID4VPHandoverCBORHex, out)

        val st = SessionTranscript.forIsoOpenId(decoded)
        println(st)
        val stHex = coseCompliantCbor.encodeToHexString(st).uppercase()

        assertEquals(stHex, specExampleSessionTranscriptCBORHex)
    }

}
