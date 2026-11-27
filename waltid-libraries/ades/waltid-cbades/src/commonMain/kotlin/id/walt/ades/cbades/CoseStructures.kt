package id.walt.ades.cbades

import id.walt.ades.cbades.cbor.Cbor
import id.walt.ades.cbades.cbor.CborException
import id.walt.ades.cbades.cbor.CborItem
import id.walt.ades.cbades.cbor.asArray
import id.walt.ades.cbades.cbor.asBytes
import id.walt.ades.cbades.cbor.asInt
import id.walt.ades.cbades.cbor.asMap
import id.walt.ades.cbades.cbor.cbor

/** Header labels and constants of RFC 9052/9360/9597/9338 and ETSI TS 119 152-1. */
object CbAdesLabels {
    const val ALG = 1
    const val CRIT = 2
    const val CONTENT_TYPE = 3
    const val KID = 4
    const val COUNTER_SIGNATURE = 11
    const val COUNTER_SIGNATURE0 = 12
    const val CWT_CLAIMS = 15
    const val X5BAG = 32
    const val X5CHAIN = 33
    const val X5T = 34
    const val X5U = 35
    const val CWT_IAT = 6

    const val X5TS = 261
    const val SR_CMS = 262
    const val SIG_PL = 263
    const val SR_ATS = 264
    const val ADO_TST = 265
    const val SIG_PID = 266
    const val SIG_D = 267
    const val U_HEADERS = 268

    // uHeaders element labels (Table 8)
    const val SIG_TST = 1
    const val VAL_DATA = 2
    const val ARC_TST = 3
    const val REFS = 4
    const val SIG_R_TST = 5
    const val RFS_TST = 6
    const val SIG_P_ST = 7

    const val COSE_SIGN_TAG = 98L
    const val COSE_SIGN1_TAG = 18L

    const val SIGD_OBJECT_ID_BY_URI = "http://uri.etsi.org/19152/ObjectIdByURI"
    const val SIGD_OBJECT_ID_BY_URI_HASH = "http://uri.etsi.org/19152/ObjectIdByURIHash"

    /** Protected header labels a CB-AdES validator understands (used to process `crit`). */
    val UNDERSTOOD_PROTECTED = setOf(ALG, CRIT, CONTENT_TYPE, KID, CWT_CLAIMS, X5BAG, X5CHAIN, X5T, X5U, X5TS, SR_CMS, SIG_PL, SR_ATS, ADO_TST, SIG_PID, SIG_D)
    val UHEADER_LABELS = setOf(SIG_TST, VAL_DATA, ARC_TST, REFS, SIG_R_TST, RFS_TST, SIG_P_ST, COUNTER_SIGNATURE, COUNTER_SIGNATURE0, X5CHAIN)
}

/** One element of the uHeaders array, kept as its exact bstr content (needed for arcTst imprints). */
class UHeaderElement(val encoded: ByteArray) {
    val map: CborItem.Map = Cbor.decode(encoded).asMap ?: throw CborException("uHeaders element is not a CBOR map")
    val label: Int? get() = map.entries.singleOrNull()?.first?.asInt
    val value: CborItem? get() = map.entries.singleOrNull()?.second

    companion object {
        fun of(label: Int, value: CborItem) = UHeaderElement(Cbor.encode(CborItem.Map(listOf(CborItem.int(label) to value))))
    }
}

/**
 * Header buckets of one signature layer: the exact protected bytes plus the unprotected map, with uHeaders
 * exposed as an ordered list.
 */
class CoseHeaders(val protectedBytes: ByteArray, val unprotected: CborItem.Map) {
    val protectedMap: CborItem.Map =
        if (protectedBytes.isEmpty()) CborItem.Map(emptyList()) else Cbor.decode(protectedBytes).asMap ?: throw CborException("protected header is not a map")

    val uHeaders: List<UHeaderElement> =
        unprotected[CbAdesLabels.U_HEADERS]?.asArray?.map { UHeaderElement(it.asBytes ?: throw CborException("uHeaders element is not a bstr")) } ?: emptyList()

    fun protected(label: Int): CborItem? = protectedMap[label]

    fun withUHeaders(elements: List<UHeaderElement>): CoseHeaders {
        val others = unprotected.entries.filterNot { it.first.asInt == CbAdesLabels.U_HEADERS }
        val entries = if (elements.isEmpty()) others else others + (CborItem.int(CbAdesLabels.U_HEADERS) to CborItem.Array(elements.map { it.encoded.cbor() }))
        return CoseHeaders(protectedBytes, CborItem.Map(entries))
    }

    fun appendUHeader(element: UHeaderElement) = withUHeaders(uHeaders + element)

    companion object {
        fun of(protected: CborItem.Map, unprotected: CborItem.Map = CborItem.Map(emptyList())) =
            CoseHeaders(if (protected.entries.isEmpty()) ByteArray(0) else Cbor.encode(protected), unprotected)
    }
}

/** COSE_Signature (signer layer) or COSE_Countersignature: [protected, unprotected, signature]. */
class CoseSignatureLayer(val headers: CoseHeaders, val signature: ByteArray) {
    fun toCbor(): CborItem = CborItem.Array(listOf(headers.protectedBytes.cbor(), headers.unprotected, signature.cbor()))
    fun withHeaders(h: CoseHeaders) = CoseSignatureLayer(h, signature)

    companion object {
        fun parse(item: CborItem): CoseSignatureLayer {
            val a = item.asArray?.takeIf { it.size == 3 } ?: throw CborException("COSE_Signature must be an array of 3 elements")
            return CoseSignatureLayer(
                CoseHeaders(a[0].asBytes ?: throw CborException("protected must be bstr"), a[1].asMap ?: throw CborException("unprotected must be a map")),
                a[2].asBytes ?: throw CborException("signature must be bstr"),
            )
        }
    }
}

/** A COSE_Sign1 or COSE_Sign message carrying CB-AdES signature(s). */
sealed class CoseMessage {
    abstract val tagged: Boolean
    abstract val payload: ByteArray?
    abstract fun toCbor(): CborItem
    fun encode(): ByteArray = Cbor.encode(toCbor())

    class Sign1(
        val headers: CoseHeaders,
        override val payload: ByteArray?,
        val signature: ByteArray,
        override val tagged: Boolean,
    ) : CoseMessage() {
        override fun toCbor(): CborItem {
            val arr = CborItem.Array(listOf(headers.protectedBytes.cbor(), headers.unprotected, payload?.cbor() ?: CborItem.NULL, signature.cbor()))
            return if (tagged) CborItem.Tag(CbAdesLabels.COSE_SIGN1_TAG, arr) else arr
        }

        fun withHeaders(h: CoseHeaders) = Sign1(h, payload, signature, tagged)
    }

    class Sign(
        val headers: CoseHeaders,
        override val payload: ByteArray?,
        val signers: List<CoseSignatureLayer>,
        override val tagged: Boolean,
    ) : CoseMessage() {
        override fun toCbor(): CborItem {
            val arr = CborItem.Array(listOf(headers.protectedBytes.cbor(), headers.unprotected, payload?.cbor() ?: CborItem.NULL, CborItem.Array(signers.map { it.toCbor() })))
            return if (tagged) CborItem.Tag(CbAdesLabels.COSE_SIGN_TAG, arr) else arr
        }

        fun withSigner(index: Int, signer: CoseSignatureLayer) = Sign(headers, payload, signers.toMutableList().also { it[index] = signer }, tagged)
    }

    companion object {
        fun decode(bytes: ByteArray): CoseMessage = fromCbor(Cbor.decode(bytes))

        fun fromCbor(root: CborItem): CoseMessage {
            var item = root
            var tagged = false
            var expectedTag: Long? = null
            if (item is CborItem.Tag) {
                if (item.tag != CbAdesLabels.COSE_SIGN_TAG && item.tag != CbAdesLabels.COSE_SIGN1_TAG) throw CborException("Unexpected CBOR tag ${item.tag}")
                tagged = true; expectedTag = item.tag; item = item.item
            }
            val a = item.asArray?.takeIf { it.size == 4 } ?: throw CborException("COSE message must be an array of 4 elements")
            val headers = CoseHeaders(a[0].asBytes ?: throw CborException("protected must be bstr"), a[1].asMap ?: throw CborException("unprotected must be a map"))
            val payload = when (val p = a[2]) {
                CborItem.NULL -> null
                is CborItem.Bytes -> p.value
                else -> throw CborException("payload must be bstr or nil")
            }
            return when (val fourth = a[3]) {
                is CborItem.Bytes -> {
                    if (expectedTag == CbAdesLabels.COSE_SIGN_TAG) throw CborException("Tag 98 on a COSE_Sign1 structure")
                    Sign1(headers, payload, fourth.value, tagged)
                }

                is CborItem.Array -> {
                    if (expectedTag == CbAdesLabels.COSE_SIGN1_TAG) throw CborException("Tag 18 on a COSE_Sign structure")
                    if (fourth.items.isEmpty()) throw CborException("COSE_Sign without signatures")
                    Sign(headers, payload, fourth.items.map(CoseSignatureLayer::parse), tagged)
                }

                else -> throw CborException("Fourth element must be a bstr (COSE_Sign1) or array (COSE_Sign)")
            }
        }
    }
}

/** RFC 9052 Sig_structure and RFC 9338 Countersign_structure builders. */
object CoseStructures {
    fun sign1ToBeSigned(protected: ByteArray, externalAad: ByteArray, payload: ByteArray): ByteArray =
        Cbor.encode(CborItem.Array(listOf("Signature1".cbor(), protected.cbor(), externalAad.cbor(), payload.cbor())))

    fun signToBeSigned(bodyProtected: ByteArray, signProtected: ByteArray, externalAad: ByteArray, payload: ByteArray): ByteArray =
        Cbor.encode(CborItem.Array(listOf("Signature".cbor(), bodyProtected.cbor(), signProtected.cbor(), externalAad.cbor(), payload.cbor())))

    /**
     * Countersign_structure over a target whose bstr fields are [targetFields] (in structure order):
     * COSE_Sign1 -> [protected, payload, signature] (V2, other_fields = [signature]);
     * COSE_Signature / COSE_Countersignature -> [protected, signature]; COSE_Sign body -> [protected, payload].
     */
    fun counterSignatureContext(targetFields: List<ByteArray>, abbreviated: Boolean = false): String {
        val v2 = targetFields.size > 2
        return when {
            abbreviated && v2 -> "CounterSignature0V2"
            abbreviated -> "CounterSignature0"
            v2 -> "CounterSignatureV2"
            else -> "CounterSignature"
        }
    }

    fun counterSignatureToBeSigned(targetFields: List<ByteArray>, counterProtected: ByteArray, externalAad: ByteArray = ByteArray(0)): ByteArray {
        require(targetFields.size >= 2) { "Countersignature target needs at least two bstr fields" }
        val items = mutableListOf(
            counterSignatureContext(targetFields).cbor(),
            targetFields[0].cbor(),
            counterProtected.cbor(),
            externalAad.cbor(),
            targetFields[1].cbor(),
        )
        if (targetFields.size > 2) items += CborItem.Array(targetFields.drop(2).map { it.cbor() })
        return Cbor.encode(CborItem.Array(items))
    }
}
