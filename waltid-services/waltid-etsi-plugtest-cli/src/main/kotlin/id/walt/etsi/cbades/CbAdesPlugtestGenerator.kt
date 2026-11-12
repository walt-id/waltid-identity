package id.walt.etsi.cbades

import id.walt.ades.cbades.CbAdesLabels
import id.walt.ades.cbades.CbAdesSignatureParameters
import id.walt.ades.cbades.CbAdesSigner
import id.walt.ades.cbades.CertificatePlacement
import id.walt.ades.cbades.ClaimedAttribute
import id.walt.ades.cbades.CoseMessage
import id.walt.ades.cbades.DetachedReferences
import id.walt.ades.cbades.SignatureLocation
import id.walt.ades.cbades.cbor.cbor
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSigningCredentials
import id.walt.ades.model.Commitment
import id.walt.ades.model.CommitmentType
import id.walt.ades.model.SignaturePolicyIdentifier
import id.walt.ades.model.SignatureProductionPlace
import id.walt.ades.validation.AdesLevel
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

/** Resources provided by the plugtest portal (Data/ folder and published documents). */
class PlugtestData(
    val toBeSigned: ByteArray,
    val signaturePolicy: ByteArray,
    val signaturePolicyId: String = "urn:oid:0.4.0.0.19102.1.1.1",
    val signaturePolicyUri: String = "https://signature-plugtests.etsi.org/api/public/signature_creation_policy.der",
)

class GeneratedSignature(val testCase: CbAdesTestCase, val bytes: ByteArray, val message: CoseMessage)

/**
 * Turns plugtest test-case definitions (CB-B-*.xml) into CB-AdES signatures. The main signer is the ETSI-issued
 * credential; second signers, counter-signers and the attribute authority come from [localPki].
 */
class CbAdesPlugtestGenerator(
    private val signer: CbAdesSigner,
    private val main: AdesSigningCredentials,
    private val localPki: LocalPlugtestPki,
    private val data: PlugtestData,
    private val signingTime: Instant = Clock.System.now(),
) {
    suspend fun generate(tc: CbAdesTestCase): GeneratedSignature {
        val payload = if (tc.detached) null else data.toBeSigned
        val secondary = listOf(main, localPki.credentials(localPki.signer2, "signer-2"))
        val message: CoseMessage = when (tc.structure) {
            TcStructure.SIGN1 -> signer.signSign1(payload, main, parameters(tc, tc.signers.single(), main), tagged = tc.tagged)
            TcStructure.SIGN -> signer.signSign(payload, tc.signers.mapIndexed { i, s ->
                val cred = secondary[i.coerceAtMost(secondary.lastIndex)]
                cred to parameters(tc, s, cred)
            }, tagged = tc.tagged)
        }
        var withArchives = message
        tc.signers.forEachIndexed { i, s ->
            val extra = s.uHeaders.count { it.name == "arcTst" } - 1
            val location = if (tc.structure == TcStructure.SIGN1) SignatureLocation.Sign1 else SignatureLocation.Signer(i)
            repeat(extra.coerceAtLeast(0)) { withArchives = signer.addArchiveTimestamp(withArchives, location) }
        }
        val withCounterSignatures = addCounterSignatures(tc, withArchives)
        return GeneratedSignature(tc, withCounterSignatures.encode(), withCounterSignatures)
    }

    private suspend fun addCounterSignatures(tc: CbAdesTestCase, message: CoseMessage): CoseMessage {
        var m = message
        val counterCreds = listOf(localPki.credentials(localPki.counter1, "counter-1"), localPki.credentials(localPki.counter2, "counter-2"))
        tc.signers.forEachIndexed { i, s ->
            val location = if (tc.structure == TcStructure.SIGN1) SignatureLocation.Sign1 else SignatureLocation.Signer(i)
            s.counterSignatures.forEachIndexed { ci, cs ->
                val cred = counterCreds[(i + ci) % counterCreds.size]
                m = signer.counterSign(m, location, cred, parameters(tc, TcSigner(cs.protected, cs.unprotected), cred))
                // nested countersignatures (CB-B-B-21)
                var path = listOf(ci)
                var nested = cs.unprotected?.child("uHeaders")?.children?.filter { it.name == "COSE_Countersignature" }.orEmpty()
                var depth = 1
                while (nested.isNotEmpty()) {
                    val n = TcSigner.parseCounter(nested.first())
                    m = signer.counterSign(m, location, counterCreds[(i + ci + depth) % counterCreds.size], parameters(tc, TcSigner(n.protected, n.unprotected), cred), nestedPath = path)
                    path = path + 0
                    nested = n.unprotected?.child("uHeaders")?.children?.filter { it.name == "COSE_Countersignature" }.orEmpty()
                    depth++
                }
            }
        }
        return m
    }

    suspend fun parameters(tc: CbAdesTestCase, s: TcSigner, credentials: AdesSigningCredentials): CbAdesSignatureParameters {
        val p = s.protected
        val u = s.uHeaders.map { it.name }
        val level = when {
            "arcTst" in u -> AdesLevel.B_LTA
            "valData" in u -> AdesLevel.B_LT
            "sigTst" in u -> AdesLevel.B_T
            else -> AdesLevel.B_B
        }
        val x5chainUnprotected = "x5chain" in u
        val x5tsAlg = p.child("x5ts")?.all("x5t")?.firstOrNull()?.value("hashAlgorithm")?.let(AdesDigestAlgorithm::fromName)
        val x5tAlg = p.child("x5t")?.value("hashAlgorithm")?.let(AdesDigestAlgorithm::fromName)
        val contentType = (p.child("contentType") ?: p.child("content-type"))?.let { it.text.ifBlank { "text/plain" } }

        val commitments = p.child("srCms")?.let { srCms ->
            srCms.all("SrCm").map { Commitment(it.child("commId")?.value("id")!!, it.child("commId")?.value("desc")) }
                .ifEmpty { listOf(Commitment(CommitmentType.PROOF_OF_ORIGIN.uri)) }
        } ?: emptyList()

        val place = p.child("sigPl")?.let {
            SignatureProductionPlace(
                country = it.value("addressCountry"), locality = it.value("addressLocality"), region = it.value("addressRegion"),
                postOfficeBoxNumber = it.value("postOfficeBoxNumber"), postalCode = it.value("postalCode"), streetAddress = it.value("streetAddress"),
            )
        }

        val claimed = p.child("srAts")?.child("claimed")?.all("NotCertifiedItem")?.map { item ->
            ClaimedAttribute(item.value("mediaType") ?: "text/plain", mapOf((item.value("label")?.toIntOrNull() ?: 1) to (item.value("value") ?: "").cbor()))
        } ?: emptyList()
        val attributeCerts = if (p.child("srAts")?.child("certified") != null) listOf(localPki.attributeCertificate(credentials.signingCertificate)) else emptyList()

        val policy = p.child("sigPId")?.let {
            val alg = it.child("digAlgVal")?.value("hashAlgorithm")?.let(AdesDigestAlgorithm::fromName) ?: AdesDigestAlgorithm.SHA256
            SignaturePolicyIdentifier(
                id = it.value("id") ?: data.signaturePolicyId,
                digestAlgorithm = alg,
                digest = alg.digest(data.signaturePolicy),
                uri = if (it.child("sigPQuals")?.child("spURI") != null) data.signaturePolicyUri else null,
                document = data.signaturePolicy,
            )
        }

        val sigD = p.child("sigD")?.let {
            DetachedReferences(
                uris = it.listValue("pars")!!,
                byHash = it.value("mId")!!.endsWith("ObjectIdByURIHash"),
                hashAlgorithm = it.value("hashM")?.let(AdesDigestAlgorithm::fromName) ?: AdesDigestAlgorithm.SHA256,
                contentTypes = it.listValue("ctys"),
            )
        }
        val crit = p.child("crit")?.all("label")?.mapNotNull { it.text.trim().toIntOrNull() }?.filter { it != CbAdesLabels.SIG_D } ?: emptyList()

        return CbAdesSignatureParameters(
            level = level,
            contentType = contentType,
            signingTime = signingTime,
            certificatePlacement = when {
                x5chainUnprotected -> CertificatePlacement.UNPROTECTED_CHAIN
                p.child("x5chain") != null -> CertificatePlacement.PROTECTED_CHAIN
                else -> CertificatePlacement.NONE
            },
            x5tDigest = x5tAlg,
            certificateDigests = x5tsAlg,
            includeKid = p.child("kid") != null,
            commitments = commitments,
            productionPlace = place,
            claimedAttributes = claimed,
            attributeCertificates = attributeCerts,
            signaturePolicy = policy,
            storeSignaturePolicy = "sigPSt" in u,
            contentTimestamp = p.child("adoTst") != null,
            detachedReferences = sigD,
            criticalLabels = crit,
        )
    }

    companion object {
        fun loadData(dataDir: File) = PlugtestData(
            toBeSigned = File(dataDir, "toBeSigned.txt").readBytes(),
            signaturePolicy = File(dataDir, "signature_creation_policy.der").readBytes(),
        )
    }
}
