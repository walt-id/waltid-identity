package id.walt.ades.cbades

import id.walt.ades.cbades.cbor.CborItem
import id.walt.ades.cbades.cbor.asArray
import id.walt.ades.cbades.cbor.asBytes
import id.walt.ades.cbades.cbor.asInt
import id.walt.ades.cbades.cbor.asLong
import id.walt.ades.cbades.cbor.asMap
import id.walt.ades.cbades.cbor.asText
import id.walt.ades.cbades.cbor.asUri
import id.walt.ades.cbades.cbor.cbor
import id.walt.ades.cbades.cbor.cborArray
import id.walt.ades.cbades.cbor.cborMap
import id.walt.ades.cbades.cbor.cborUri
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.model.Commitment
import id.walt.ades.model.SignaturePolicyIdentifier
import id.walt.ades.model.SignatureProductionPlace
import id.walt.ades.validation.ValidationData
import id.walt.ades.x509.OcspResponse
import id.walt.ades.x509.X509Certificate
import id.walt.ades.x509.X509Crl

/** A claimed signer attribute (srAts.claimed NotCertifiedItem): media type plus labelled values. */
data class ClaimedAttribute(val mediaType: String, val values: Map<Any, CborItem>)

/** Detached payload reference (sigD). */
data class DetachedReferences(
    val uris: List<String>,
    val byHash: Boolean = false,
    val hashAlgorithm: AdesDigestAlgorithm = AdesDigestAlgorithm.SHA256,
    val contentTypes: List<String?>? = null,
) {
    val mechanism: String get() = if (byHash) CbAdesLabels.SIGD_OBJECT_ID_BY_URI_HASH else CbAdesLabels.SIGD_OBJECT_ID_BY_URI
}

/** Encoders / decoders for the CB-AdES components of ETSI TS 119 152-1 clause 5. */
object CbAdesComponents {
    fun hashAlgItem(alg: AdesDigestAlgorithm): CborItem = alg.coseId?.cbor() ?: alg.displayName.cbor()

    fun hashAlg(item: CborItem?): AdesDigestAlgorithm? = when {
        item == null -> null
        item.asInt != null -> AdesDigestAlgorithm.fromCoseIdOrNull(item.asInt!!)
        item.asText != null -> runCatching { AdesDigestAlgorithm.fromName(item.asText!!) }.getOrNull()
        else -> null
    }

    /** COSE_CertHash = [hashAlg, hashValue]. */
    suspend fun certHash(cert: X509Certificate, alg: AdesDigestAlgorithm): CborItem = cborArray(hashAlgItem(alg), cert.digest(alg).cbor())

    fun x5chain(certs: List<X509Certificate>): CborItem =
        if (certs.size == 1) certs.single().encoded.cbor() else CborItem.Array(certs.map { it.encoded.cbor() })

    fun parseX5chain(item: CborItem?): List<X509Certificate>? = when (item) {
        null -> null
        is CborItem.Bytes -> listOf(X509Certificate(item.value))
        is CborItem.Array -> item.items.map { X509Certificate(it.asBytes ?: error("x5chain element is not a bstr")) }
        else -> error("x5chain must be bstr or array")
    }

    fun oId(id: String, description: String? = null, docRefs: List<String> = emptyList()): CborItem = cborMap(
        1 to id.cborUri(),
        2 to description?.cbor(),
        3 to docRefs.takeIf { it.isNotEmpty() }?.let { refs -> CborItem.Array(refs.map { it.cborUri() }) },
    )

    fun srCms(commitments: List<Commitment>): CborItem =
        CborItem.Array(commitments.map { cborMap(1 to oId(it.id, it.description, it.documentationReferences)) })

    fun sigPl(p: SignatureProductionPlace): CborItem = cborMap(
        1 to p.country?.cbor(), 2 to p.locality?.cbor(), 3 to p.region?.cbor(),
        4 to p.postOfficeBoxNumber?.cbor(), 5 to p.postalCode?.cbor(), 6 to p.streetAddress?.cbor(),
    )

    fun srAts(claimed: List<ClaimedAttribute>, certifiedAttributeCertificates: List<ByteArray>, signedAssertions: List<ClaimedAttribute>): CborItem? {
        fun items(list: List<ClaimedAttribute>) = list.takeIf { it.isNotEmpty() }?.let { l ->
            CborItem.Array(l.map { a -> cborArray(a.mediaType.cbor(), cborMap(*a.values.map { it.key to it.value }.toTypedArray())) })
        }
        val certified = certifiedAttributeCertificates.takeIf { it.isNotEmpty() }?.let { l -> CborItem.Array(l.map { cborMap(1 to pkiOb(it)) }) }
        return cborMap(1 to certified, 2 to items(signedAssertions), 3 to items(claimed)).takeIf { it.entries.isNotEmpty() }
    }

    fun sigPId(policy: SignaturePolicyIdentifier): CborItem {
        val quals = buildList {
            policy.uri?.let { add(cborMap(1 to it.cborUri())) }
            policy.userNotice?.let { add(cborMap(2 to cborMap(2 to it.cbor()))) }
        }
        return cborMap(
            1 to oId(policy.id, policy.description),
            2 to cborArray(hashAlgItem(policy.digestAlgorithm), policy.digest.cbor()),
            4 to quals.takeIf { it.isNotEmpty() }?.let(CborItem::Array),
        )
    }

    fun sigPSt(document: ByteArray): CborItem = cborMap(1 to cborMap(1 to document.cbor()))

    fun sigD(refs: DetachedReferences, digests: List<ByteArray>?): CborItem = cborMap(
        1 to refs.mechanism.cborUri(),
        2 to CborItem.Array(refs.uris.map { it.cbor() }),
        3 to if (refs.byHash) hashAlgItem(refs.hashAlgorithm) else null,
        4 to if (refs.byHash) CborItem.Array(digests!!.map { it.cbor() }) else null,
        5 to refs.contentTypes?.let { cts -> CborItem.Array(cts.map { it?.cbor() ?: CborItem.NULL }) },
    )

    fun pkiOb(der: ByteArray): CborItem = cborMap(1 to der.cbor())

    /** tstContainer = {1: [+TstToken]} with RFC 3161 tokens (no type/encoding/specRef members). */
    fun tstContainer(tokens: List<ByteArray>): CborItem = cborMap(1 to CborItem.Array(tokens.map { cborMap(1 to it.cbor()) }))

    fun parseTstContainer(item: CborItem?): List<ByteArray>? =
        item?.asMap?.get(1)?.asArray?.map { it.asMap?.get(1)?.asBytes ?: error("TstToken without val") }

    fun valData(data: ValidationData): CborItem? {
        val xVals = data.certificates.takeIf { it.isNotEmpty() }?.let { c -> CborItem.Array(c.map { cborMap(1 to pkiOb(it.encoded)) }) }
        val rVals = cborMap(
            1 to data.crls.takeIf { it.isNotEmpty() }?.let { c -> CborItem.Array(c.map { pkiOb(it.encoded) }) },
            2 to data.ocsps.takeIf { it.isNotEmpty() }?.let { c -> CborItem.Array(c.map { pkiOb(it.encoded) }) },
        ).takeIf { it.entries.isNotEmpty() }
        return cborMap(1 to xVals, 2 to rVals).takeIf { it.entries.isNotEmpty() }
    }

    fun parseValData(item: CborItem): ValidationData {
        val m = item.asMap ?: error("valData is not a map")
        fun val1(x: CborItem) = x.asMap?.get(1)?.asBytes ?: error("pkiOb without val")
        val certs = m[1]?.asArray?.mapNotNull { x -> x.asMap?.get(1)?.let { X509Certificate(val1(it)) } } ?: emptyList()
        val r = m[2]?.asMap
        val crls = r?.get(1)?.asArray?.map { X509Crl(val1(it)) } ?: emptyList()
        val ocsps = r?.get(2)?.asArray?.map { OcspResponse(val1(it)) } ?: emptyList()
        return ValidationData(certs, crls, ocsps)
    }

    fun cwtClaims(iat: Long): CborItem = cborMap(CbAdesLabels.CWT_IAT to iat.cbor())
    fun iat(protected: CborItem.Map): Long? = protected[CbAdesLabels.CWT_CLAIMS]?.asMap?.get(CbAdesLabels.CWT_IAT)?.asLong

    fun uri(item: CborItem?): String? = item?.asUri
}
