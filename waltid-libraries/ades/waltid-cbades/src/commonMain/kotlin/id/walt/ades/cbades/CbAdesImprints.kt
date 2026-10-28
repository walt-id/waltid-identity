package id.walt.ades.cbades

import id.walt.ades.cbades.cbor.Cbor
import id.walt.ades.cbades.cbor.CborItem
import id.walt.ades.cbades.cbor.asArray
import id.walt.ades.cbades.cbor.asMap
import id.walt.ades.cbades.cbor.asText
import id.walt.ades.cbades.cbor.cbor
import id.walt.ades.transport.AdesTransport

/** Resolves sigD URI references (ObjectIdByURI / ObjectIdByURIHash) to the referenced data object bytes. */
fun interface DocumentResolver {
    suspend fun resolve(uri: String): ByteArray

    companion object {
        fun http(transport: AdesTransport) = DocumentResolver { transport.get(it) }
        fun of(documents: Map<String, ByteArray>, fallback: DocumentResolver? = null) = DocumentResolver { uri ->
            documents[uri] ?: fallback?.resolve(uri) ?: throw IllegalArgumentException("No document for $uri")
        }
    }
}

/**
 * How the arcTst message-imprint input is serialized. TS 119 152-1 V1.1.1 5.3.5.3 says "the CBOR byte string resulting
 * of step 11)"; the spec editor confirmed in the 2026 plugtest forum that step 12) is meant, i.e. the bstr-wrapped array.
 */
enum class ArcTstImprintMode {
    /** Digest over the encoded array wrapped in a CBOR byte string (step 12, authoritative reading). */
    BSTR_WRAPPED,
    /** Digest over the encoded CBOR array only (used by early plugtest implementations and the initial checker). */
    ARRAY,
}

/** Identifies one signature inside a COSE message. */
sealed interface SignatureLocation {
    data object Sign1 : SignatureLocation
    data class Signer(val index: Int) : SignatureLocation
}

/** Resolved data of one CB-AdES signature layer used for signing inputs and message imprints. */
internal class LayerView(
    val isSign1: Boolean,
    val bodyProtected: ByteArray,
    val signProtected: ByteArray?,
    val headers: CoseHeaders,
    val signature: ByteArray,
) {
    /** Protected map that carries the CB-AdES signed header parameters of this signature. */
    val signerProtected: CborItem.Map get() = headers.protectedMap
}

internal fun CoseMessage.layer(location: SignatureLocation): LayerView = when (this) {
    is CoseMessage.Sign1 -> {
        require(location == SignatureLocation.Sign1) { "COSE_Sign1 has a single signature" }
        LayerView(true, headers.protectedBytes, null, headers, signature)
    }

    is CoseMessage.Sign -> {
        val signer = signers[(location as? SignatureLocation.Signer ?: error("COSE_Sign requires a signer index")).index]
        LayerView(false, headers.protectedBytes, signer.headers.protectedBytes, signer.headers, signer.signature)
    }
}

internal fun CoseMessage.locations(): List<SignatureLocation> = when (this) {
    is CoseMessage.Sign1 -> listOf(SignatureLocation.Sign1)
    is CoseMessage.Sign -> signers.indices.map { SignatureLocation.Signer(it) }
}

internal fun CoseMessage.replaceHeaders(location: SignatureLocation, headers: CoseHeaders): CoseMessage = when (this) {
    is CoseMessage.Sign1 -> withHeaders(headers)
    is CoseMessage.Sign -> {
        val i = (location as SignatureLocation.Signer).index
        withSigner(i, signers[i].withHeaders(headers))
    }
}

/** Message-imprint inputs of the CB-AdES time-stamp containers. */
object CbAdesImprints {
    /** sigTst (5.3.3): the COSE signature value. */
    fun sigTst(signature: ByteArray): ByteArray = signature

    /** adoTst (5.2.6): the payload (or concatenated sigD objects) encapsulated in a CBOR byte string. */
    fun adoTst(payload: ByteArray): ByteArray = Cbor.encode(payload.cbor())

    /** arcTst (5.3.5.3) over the components of a signature layer and the uHeaders elements preceding the new arcTst. */
    fun arcTst(
        isSign1: Boolean,
        bodyProtected: ByteArray,
        signProtected: ByteArray?,
        externalAad: ByteArray,
        payload: ByteArray,
        signature: ByteArray,
        precedingUHeaders: List<UHeaderElement>,
        mode: ArcTstImprintMode = ArcTstImprintMode.BSTR_WRAPPED,
    ): ByteArray {
        val items = mutableListOf<CborItem>((if (isSign1) "Signature1" else "Signature").cbor(), bodyProtected.cbor())
        if (!isSign1) items += (signProtected ?: ByteArray(0)).cbor()
        items += externalAad.cbor()
        items += payload.cbor()
        items += signature.cbor()
        if (precedingUHeaders.isEmpty()) items += ByteArray(0).cbor() else precedingUHeaders.forEach { items += it.encoded.cbor() }
        val array = Cbor.encode(CborItem.Array(items))
        return if (mode == ArcTstImprintMode.ARRAY) array else Cbor.encode(array.cbor())
    }

    /** sigRTst (A.1.2.1): signature value, then sigTst and refs elements. */
    fun sigRTst(signature: ByteArray, uHeaders: List<UHeaderElement>): ByteArray {
        val refs = uHeaders.filter { it.label == CbAdesLabels.SIG_TST || it.label == CbAdesLabels.REFS }
        val items = listOf(signature.cbor()) + (if (refs.isEmpty()) listOf(ByteArray(0).cbor()) else refs.map { it.encoded.cbor() })
        return Cbor.encode(CborItem.Array(items))
    }

    /** rfsTst (A.1.2.2): sigTst and refs elements. */
    fun rfsTst(uHeaders: List<UHeaderElement>): ByteArray {
        val refs = uHeaders.filter { it.label == CbAdesLabels.SIG_TST || it.label == CbAdesLabels.REFS }
        return Cbor.encode(CborItem.Array(if (refs.isEmpty()) listOf(ByteArray(0).cbor()) else refs.map { it.encoded.cbor() }))
    }
}

/** Payload handling shared by signer and validator. */
internal object CbAdesPayloads {
    class SigD(val mechanism: String, val uris: List<String>, val hashAlgorithm: CborItem?, val digests: List<ByteArray>?, val contentTypes: List<CborItem>?)

    fun parseSigD(item: CborItem?): SigD? {
        val m = item?.asMap ?: return null
        return SigD(
            CbAdesComponents.uri(m[1]) ?: error("sigD.mId missing"),
            m[2]?.asArray?.map { it.asText ?: error("sigD.pars element is not a text string") } ?: error("sigD.pars missing"),
            m[3],
            m[4]?.asArray?.map { (it as? CborItem.Bytes)?.value ?: error("sigD.hashV element is not a bstr") },
            m[5]?.asArray,
        )
    }

    /** Concatenated dereferenced objects (5.2.8.2.2), used as payload for ObjectIdByURI and for adoTst/arcTst. */
    suspend fun concatenated(sigD: SigD, resolver: DocumentResolver): ByteArray =
        sigD.uris.map { resolver.resolve(it) }.fold(ByteArray(0)) { a, b -> a + b }
}
