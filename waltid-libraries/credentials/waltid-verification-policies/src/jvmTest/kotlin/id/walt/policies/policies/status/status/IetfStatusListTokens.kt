package id.walt.policies.policies.status.status

import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
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
import id.walt.statuslist.codec.TokenStatusListCodec
import id.walt.statuslist.model.IndexedStatus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.encodeToByteArray
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
        val lst = TokenStatusListCodec.encode(
            statuses = listOf(IndexedStatus(index, status)),
            statusSize = bits,
            listSizeBytes = listSizeBytes,
        )
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

    @Serializable
    private data class CwtStatusListToken(
        @SerialName("status_list")
        @CborLabel(65533)
        val statusList: IETFStatusContent,
    )
}
