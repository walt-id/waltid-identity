@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.mso

import id.walt.cose.CoseKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.SerialName

/** Diagnostic-only MSO device-key decoder; all other COSE_Key consumers retain their strict serializer. */
internal object ItbDiagnosticMsoDeviceKeySerializer : KSerializer<CoseKey> {
    override val descriptor: SerialDescriptor = DiagnosticDeviceKey.serializer().descriptor

    override fun serialize(encoder: Encoder, value: CoseKey) =
        encoder.encodeSerializableValue(CoseKey.serializer(), value)

    override fun deserialize(decoder: Decoder): CoseKey =
        decoder.decodeSerializableValue(DiagnosticDeviceKey.serializer()).toCoseKey()
}

/** Mirrors the COSE_Key fields so only the MSO device key's label 2 interpretation differs. */
@Serializable
private data class DiagnosticDeviceKey(
    @CborLabel(1) val kty: Int,
    @CborLabel(2) @Serializable(with = ItbDiagnosticMsoKidSerializer::class) val kid: ByteArray? = null,
    @CborLabel(3) val alg: Int? = null,
    @CborLabel(4) val key_ops: List<Int>? = null,
    @CborLabel(5) @SerialName("Base IV") @ByteString val baseIv: ByteArray? = null,
    @CborLabel(-1) val crv: Int? = null,
    @CborLabel(-2) @ByteString val x: ByteArray? = null,
    @CborLabel(-3) @ByteString val y: ByteArray? = null,
    @CborLabel(-4) @ByteString val d: ByteArray? = null,
) {
    fun toCoseKey() = CoseKey(kty, kid, alg, key_ops, baseIv, crv, x, y, d)
}

private object ItbDiagnosticMsoKidSerializer : KSerializer<ByteArray?> {
    override val descriptor = PrimitiveSerialDescriptor("ItbDiagnosticMsoKid", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray?) {
        if (value == null) encoder.encodeNull()
        else encoder.encodeSerializableValue(CborElement.serializer(), CborByteString(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray? =
        when (val value = decoder.decodeSerializableValue(CborElement.serializer())) {
            is CborByteString -> value.toByteArray()
            is CborString -> {
                // TODO [WAL-1423]: Remove when the ITB issuer encodes COSE_Key label 2 as bstr.
                // Interpret this one text value in memory; never rewrite the signed MSO payload.
                require(value.value.isNotEmpty()) { "Diagnostic MSO device COSE_Key kid must not be empty" }
                value.value.encodeToByteArray()
            }
            else -> error("MSO device COSE_Key kid must be a byte or diagnostic text string")
        }
}
