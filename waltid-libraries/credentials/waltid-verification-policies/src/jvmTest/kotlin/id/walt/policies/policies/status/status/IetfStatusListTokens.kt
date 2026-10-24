package id.walt.policies.policies.status.status

import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.ByteStringElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.toDataElement
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.doc.MDocBuilder
import id.walt.mdoc.doc.MDocTypes
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.MSO
import id.walt.mdoc.mso.Status
import id.walt.mdoc.mso.StatusListInfo
import id.walt.mdoc.mso.ValidityInfo
import id.walt.policies.policies.status.model.IETFStatusContent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.encodeToByteArray
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalSerializationApi::class)
object IetfStatusListTokens {

    fun unsignedCwt(
        index: Int,
        status: UInt,
        bits: Int = 1,
        listSizeBytes: Int = 16,
    ): ByteArray {
        val lst = encodeTokenStatusList(index = index, status = status, bits = bits, listSizeBytes = listSizeBytes)
        val payload = coseCompliantCbor.encodeToByteArray(
            CwtStatusListToken(
                statusList = IETFStatusContent(size = bits, list = lst),
            )
        )
        val protected = coseCompliantCbor.encodeToByteArray(CoseHeaders(algorithm = Cose.Algorithm.EdDSA))
        return CoseSign1(
            protected = protected,
            unprotected = CoseHeaders(),
            payload = payload,
            signature = ByteArray(64),
        ).toTagged()
    }

    fun mdocWithStatus(index: UInt, uri: String): MDoc {
        val now = Clock.System.now()
        val builder = MDocBuilder(MDocTypes.ISO_MDL)
            .addItemToSign("org.iso.18013.5.1", "family_name", "Doe".toDataElement())
        val deviceKeyInfo = DeviceKeyInfo(
            deviceKey = mapOf("kty" to 2.toDataElement()).toDataElement(),
        )
        val mso = MSO.createFor(
            nameSpaces = builder.nameSpacesMap,
            deviceKeyInfo = deviceKeyInfo,
            docType = MDocTypes.ISO_MDL,
            validityInfo = ValidityInfo(now, now, now.plus(365.days)),
            status = Status(
                statusList = StatusListInfo(index = index, uri = uri),
            ),
        )
        val issuerAuth = COSESign1(
            listOf(
                ByteStringElement(byteArrayOf(0xa0.toByte())),
                MapElement(emptyMap()),
                ByteStringElement(mso.toMapElement().toEncodedCBORElement().toCBOR()),
                ByteStringElement(ByteArray(64)),
            )
        )
        return builder.build(issuerAuth)
    }

    /**
     * Packs IETF Token Status List bits LSB-first and zlib-compresses them to base64url,
     * matching [id.walt.policies.policies.status.expansion.TokenStatusListExpansionAlgorithm].
     */
    private fun encodeTokenStatusList(
        index: Int,
        status: UInt,
        bits: Int,
        listSizeBytes: Int,
    ): String {
        val bytes = ByteArray(listSizeBytes)
        val bitStart = index * bits
        for (bitOffset in 0 until bits) {
            if ((status.toInt() shr bitOffset) and 1 == 1) {
                val position = bitStart + bitOffset
                val byteIndex = position / 8
                val bitIndex = position % 8
                bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            }
        }
        return zlibCompress(bytes).encodeToBase64Url()
    }

    private fun zlibCompress(input: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }
        deflater.end()
        return output.toByteArray()
    }

    @Serializable
    private data class CwtStatusListToken(
        @SerialName("status_list")
        @CborLabel(65533)
        val statusList: IETFStatusContent,
    )
}
