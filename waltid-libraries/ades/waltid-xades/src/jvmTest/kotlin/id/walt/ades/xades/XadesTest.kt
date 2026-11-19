package id.walt.ades.xades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.model.Commitment
import id.walt.ades.model.CommitmentType
import id.walt.ades.model.SignaturePolicyIdentifier
import id.walt.ades.model.SignatureProductionPlace
import id.walt.ades.test.TestEntity
import id.walt.ades.test.TestPki
import id.walt.ades.tsp.Rfc3161TimestampClient
import id.walt.ades.validation.AdesLevel
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.CheckStatus
import id.walt.ades.validation.DocumentValidationResult
import id.walt.ades.validation.Indication
import id.walt.ades.validation.SubIndication
import id.walt.ades.validation.TimestampType
import id.walt.ades.xades.XadesNamespaces.DS
import id.walt.ades.xades.XadesNamespaces.XADES
import id.walt.ades.xades.XadesNamespaces.XADES_141
import id.walt.ades.xades.Xml.child
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.w3c.dom.Element

class XadesTest {
    private val pki = shared
    private val tsa = Rfc3161TimestampClient(pki.tsaUrl, pki.transport)

    private val xml = """<?xml version="1.0" encoding="UTF-8"?><invoice xmlns="urn:test:invoice" id="inv-1"><amount currency="EUR">42.00</amount><!-- c --><note>Grüße &amp; more</note></invoice>"""
        .encodeToByteArray()
    private val pdf = "%PDF-1.7 fake pdf content".encodeToByteArray()

    private fun options(level: AdesLevel, contentTimestamp: Boolean = false, block: XadesSignatureOptionsBuilder.() -> Unit = {}) =
        XadesSignatureOptionsBuilder(level, contentTimestamp).apply(block).build()

    inner class XadesSignatureOptionsBuilder(val level: AdesLevel, val contentTimestamp: Boolean) {
        var canonicalization = XadesCanonicalization.EXCLUSIVE
        var policy: SignaturePolicyIdentifier? = null
        var policySpec: String? = null
        var place: SignatureProductionPlace? = null
        var roles: List<String> = emptyList()
        var commitments: List<Commitment> = emptyList()
        var issuerSerial = false

        fun build() = XadesSignatureOptions(
            level = level,
            canonicalization = canonicalization,
            signaturePolicy = policy,
            signaturePolicyDocSpecification = policySpec,
            productionPlace = place,
            claimedRoles = roles,
            commitments = commitments,
            includeIssuerSerial = issuerSerial,
            contentTimestamp = contentTimestamp,
            augmentation = augmentationOptions(canonicalization),
        )
    }

    private fun augmentationOptions(c14n: XadesCanonicalization = XadesCanonicalization.EXCLUSIVE) = XadesAugmentationOptions(
        timestampSource = tsa,
        canonicalization = c14n,
        transport = pki.transport,
        trustAnchors = listOf(pki.root.certificate),
    )

    private val online = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate), transport = pki.transport)
    private val offline = AdesValidationPolicy(trustAnchors = listOf(pki.root.certificate))

    private suspend fun signer(options: XadesSignatureOptions, entity: TestEntity = pki.signer) =
        XadesSigner(pki.credentials(entity), options)

    private suspend fun sign(packaging: XadesPackaging, level: AdesLevel, options: XadesSignatureOptions = options(level)): ByteArray {
        val s = signer(options)
        return when (packaging) {
            XadesPackaging.ENVELOPED -> s.signEnveloped(xml)
            XadesPackaging.ENVELOPING -> s.signEnveloping(XadesDocument("data.bin", pdf, "application/pdf"), XadesDocument("invoice.xml", xml, "text/xml"))
            XadesPackaging.DETACHED -> s.signDetached(XadesDocument("my document.pdf", pdf, "application/pdf"))
        }
    }

    private val detachedDocs = listOf(XadesDocument("my document.pdf", pdf, "application/pdf"))

    private fun DocumentValidationResult.describe() = signatures.joinToString("\n") { s ->
        "${s.indication}/${s.subIndication} level=${s.level}\n" + s.checks.joinToString("\n") { "  ${it.status} ${it.id}: ${it.message}" }
    } + documentChecks.joinToString("\n")

    private fun assertValid(result: DocumentValidationResult, level: AdesLevel) {
        assertTrue(result.isValid, result.describe())
        assertEquals(level, result.signatures.single().level, result.describe())
    }

    private suspend fun validate(signed: ByteArray, policy: AdesValidationPolicy = online, detached: List<XadesDocument> = emptyList()) =
        XadesValidator(policy).validate(signed, detached)

    @Test
    fun allLevelsAndPackagings() = runTest(timeout = 120.seconds) {
        for (packaging in XadesPackaging.entries) for (level in AdesLevel.entries) {
            val signed = sign(packaging, level)
            val detached = if (packaging == XadesPackaging.DETACHED) detachedDocs else emptyList()
            val policy = if (level >= AdesLevel.B_LT) offline else online
            val result = validate(signed, policy, detached)
            assertValid(result, level)
            val s = result.signatures.single()
            assertEquals(pki.signer.certificate, s.signingCertificate)
            if (level >= AdesLevel.B_T) assertTrue(s.timestamps.isNotEmpty() && s.timestamps.all { it.passed }, result.describe())
            if (level == AdesLevel.B_LTA) assertTrue(s.timestamps.any { it.type == TimestampType.ARCHIVE })
            assertSantuarioAccepts(signed, detached)
        }
    }

    /** Independent cross-check: Apache Santuario's own XMLDSig core validation accepts what we produce. */
    private fun assertSantuarioAccepts(signed: ByteArray, detached: List<XadesDocument>) {
        val doc = Xml.parse(signed)
        val sig = XadesSignatureElement.findAll(doc).single()
        assertTrue(sig.registerIds().isEmpty())
        val xmlSignature = sig.santuario(XadesSigner.detachedMap(detached))
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(pki.signer.der)) as X509Certificate
        assertTrue(xmlSignature.checkSignatureValue(cert), "Santuario rejects the signature")
    }

    @Test
    fun augmentationFromBasicToRepeatedLta() = runTest(timeout = 120.seconds) {
        for (packaging in XadesPackaging.entries) {
            val detached = if (packaging == XadesPackaging.DETACHED) detachedDocs else emptyList()
            val bb = sign(packaging, AdesLevel.B_B)
            val augmenter = XadesAugmenter(augmentationOptions())
            val bt = augmenter.augment(bb, AdesLevel.B_T, detached)
            assertValid(validate(bt, detached = detached), AdesLevel.B_T)
            val blt = augmenter.augment(bt, AdesLevel.B_LT, detached)
            assertValid(validate(blt, offline, detached), AdesLevel.B_LT)
            val blta = augmenter.augment(blt, AdesLevel.B_LTA, detached)
            assertValid(validate(blta, offline, detached), AdesLevel.B_LTA)
            // renewal with another TSA: its validation data follows the new ArchiveTimeStamp (EN 319 132-1 5.5.1.2)
            val renewer = XadesAugmenter(
                XadesAugmentationOptions(
                    timestampSource = tsa, archiveTimestampSource = Rfc3161TimestampClient(pki.tsa2Url, pki.transport),
                    transport = pki.transport, trustAnchors = listOf(pki.root.certificate),
                ),
            )
            val renewed = renewer.augment(blta, AdesLevel.B_LTA, detached)
            val result = validate(renewed, offline.strict(), detached)
            assertValid(result, AdesLevel.B_LTA)
            val usp = XadesSignatureElement.findAll(Xml.parse(renewed)).single().unsignedSignaturePropertyElements
            val archives = usp.filter { it.localName == "ArchiveTimeStamp" && it.namespaceURI == XADES_141 }
            assertEquals(2, archives.size)
            val afterLast = usp[usp.indexOf(archives.last()) + 1]
            assertEquals("TimeStampValidationData", afterLast.localName)
            assertEquals(3, result.signatures.single().timestamps.count { it.passed }, result.describe())
            assertTrue(result.signatures.single().timestamps.any { it.tsaCertificate == pki.tsa2.certificate })
            // augmenting to a lower level is a no-op
            assertTrue(augmenter.augment(renewed, AdesLevel.B_LT, detached).contentEquals(renewed))
        }
    }

    @Test
    fun c14nVariants() = runTest(timeout = 60.seconds) {
        for (c14n in XadesCanonicalization.entries) {
            val signed = sign(XadesPackaging.ENVELOPED, AdesLevel.B_LTA, options(AdesLevel.B_LTA) { canonicalization = c14n })
            assertValid(validate(signed, offline.strict()), AdesLevel.B_LTA)
        }
    }

    @Test
    fun signedPropertiesAreProducedAndValidated() = runTest {
        val policyDoc = "signature policy text".encodeToByteArray()
        val policy = SignaturePolicyIdentifier.forDocument("1.2.3.4.5", policyDoc, uri = "http://policy.test/p.pdf", description = "test policy", embedDocument = true)
        val options = options(AdesLevel.B_LT, contentTimestamp = true) {
            this.policy = policy
            policySpec = "urn:oid:1.2.3.4.5.6"
            place = SignatureProductionPlace(country = "AT", locality = "Vienna", postalCode = "1010")
            roles = listOf("CEO")
            commitments = listOf(Commitment(CommitmentType.PROOF_OF_APPROVAL.uri, "approved"))
            issuerSerial = true
        }
        val signed = sign(XadesPackaging.ENVELOPED, AdesLevel.B_LT, options)
        val result = validate(signed, offline)
        assertValid(result, AdesLevel.B_LT)
        val s = result.signatures.single()
        assertTrue(s.checks.any { it.id == "xades:signature-policy" && it.status == CheckStatus.PASSED }, result.describe())
        assertTrue(s.timestamps.any { it.type == TimestampType.CONTENT && it.passed })
        assertNotNull(s.claimedSigningTime)

        val sig = XadesSignatureElement.findAll(Xml.parse(signed)).single()
        val ssp = sig.signedSignatureProperties!!
        for (name in listOf("SigningTime", "SigningCertificateV2", "SignaturePolicyIdentifier", "SignatureProductionPlaceV2", "SignerRoleV2")) {
            assertNotNull(ssp.child(XADES, name), name)
        }
        assertNotNull(ssp.child(XADES, "SigningCertificateV2")!!.child(XADES, "Cert")!!.child(XADES, "IssuerSerialV2"))
        val sdop = sig.signedDataObjectProperties!!
        for (name in listOf("DataObjectFormat", "CommitmentTypeIndication", "AllDataObjectsTimeStamp")) assertNotNull(sdop.child(XADES, name), name)
        assertEquals("text/xml", sdop.child(XADES, "DataObjectFormat")!!.child(XADES, "MimeType")!!.textContent)
        assertNotNull(sig.unsignedSignatureProperties!!.child(XADES_141, "SignaturePolicyStore"))
        // validation data of the content time-stamp: first unsigned property, pointing to the signed container
        val first = sig.unsignedSignaturePropertyElements.first()
        assertEquals("TimeStampValidationData", first.localName)
        assertEquals("#" + sdop.child(XADES, "AllDataObjectsTimeStamp")!!.getAttribute("Id"), first.getAttribute("URI"))

        // a different policy document in the (unsigned) store is detected
        val tampered = Xml.parse(signed)
        val store = XadesSignatureElement.findAll(tampered).single().unsignedSignatureProperties!!.child(XADES_141, "SignaturePolicyStore")!!
        store.child(XADES_141, "SignaturePolicyDocument")!!.textContent = Xml.b64("other policy".encodeToByteArray())
        val bad = validate(Xml.serialize(tampered))
        assertEquals(SubIndication.POLICY_PROCESSING_ERROR, bad.subIndication, bad.describe())
    }

    @Test
    fun tamperedContentIsDetected() = runTest {
        val signed = sign(XadesPackaging.ENVELOPED, AdesLevel.B_B).decodeToString()
        val tampered = signed.replace("42.00", "43.00").encodeToByteArray()
        val result = validate(tampered)
        assertEquals(Indication.TOTAL_FAILED, result.indication)
        assertEquals(SubIndication.HASH_FAILURE, result.subIndication, result.describe())

        val enveloping = sign(XadesPackaging.ENVELOPING, AdesLevel.B_B)
        val doc = Xml.parse(enveloping)
        val obj = XadesSignatureElement.findAll(doc).single().objects.first()
        obj.textContent = Xml.b64("%PDF-1.7 other".encodeToByteArray())
        assertEquals(SubIndication.HASH_FAILURE, validate(Xml.serialize(doc)).subIndication)

        val detached = sign(XadesPackaging.DETACHED, AdesLevel.B_B)
        val wrongContent = validate(detached, detached = listOf(XadesDocument("my document.pdf", "changed".encodeToByteArray())))
        assertEquals(SubIndication.HASH_FAILURE, wrongContent.subIndication, wrongContent.describe())
        val missing = validate(detached)
        assertEquals(Indication.INDETERMINATE, missing.indication)
        assertEquals(SubIndication.SIGNED_DATA_NOT_FOUND, missing.subIndication, missing.describe())
        // resolver-provided detached content
        val resolved = XadesValidator(online) { uri -> if (XadesSigner.decodeUri(uri) == "my document.pdf") pdf else null }.validate(detached)
        assertTrue(resolved.isValid, resolved.describe())
    }

    @Test
    fun tamperedSignedPropertiesAreDetected() = runTest {
        val signed = sign(XadesPackaging.ENVELOPED, AdesLevel.B_B)
        val doc = Xml.parse(signed)
        val st = XadesSignatureElement.findAll(doc).single().signedSignatureProperties!!.child(XADES, "SigningTime")!!
        st.textContent = "2020-01-01T00:00:00Z"
        val result = validate(Xml.serialize(doc))
        assertEquals(Indication.TOTAL_FAILED, result.indication)
        assertEquals(SubIndication.HASH_FAILURE, result.subIndication, result.describe())
    }

    @Test
    fun tamperedSignatureValueIsDetected() = runTest {
        val doc = Xml.parse(sign(XadesPackaging.ENVELOPED, AdesLevel.B_B))
        val sv = XadesSignatureElement.findAll(doc).single().signatureValue
        val bytes = Xml.unb64(sv.textContent).also { it[5] = (it[5].toInt() xor 1).toByte() }
        sv.textContent = Xml.b64(bytes)
        assertEquals(SubIndication.SIG_CRYPTO_FAILURE, validate(Xml.serialize(doc)).subIndication)
    }

    @Test
    fun wrongSigningCertificateReference() = runTest {
        // KeyInfo carries a different certificate than the one referenced by SigningCertificateV2
        val doc = Xml.parse(sign(XadesPackaging.ENVELOPED, AdesLevel.B_B))
        val sig = XadesSignatureElement.findAll(doc).single()
        sig.keyInfo!!.child(DS, "X509Data")!!.child(DS, "X509Certificate")!!.textContent = Xml.b64(pki.secondSigner.der)
        val result = validate(Xml.serialize(doc))
        assertEquals(Indication.INDETERMINATE, result.indication, result.describe())
        assertEquals(SubIndication.NO_SIGNING_CERTIFICATE_FOUND, result.subIndication)

        // correctly signed, but the signed CertDigest does not match the signing certificate
        val doc2 = Xml.parse(sign(XadesPackaging.ENVELOPED, AdesLevel.B_B))
        val sig2 = XadesSignatureElement.findAll(doc2).single()
        val digestValue = sig2.signedSignatureProperties!!.child(XADES, "SigningCertificateV2")!!.child(XADES, "Cert")!!
            .child(XADES, "CertDigest")!!.child(DS, "DigestValue")!!
        digestValue.textContent = Xml.b64(AdesDigestAlgorithm.SHA256.digest(pki.secondSigner.der))
        XadesSigner.finalizeSignature(sig2, emptyMap(), pki.credentials())
        val result2 = validate(Xml.serialize(doc2))
        assertEquals(SubIndication.NO_SIGNING_CERTIFICATE_FOUND, result2.subIndication, result2.describe())
        assertTrue(result2.signatures.single().checks.any { it.id == "xades:signing-certificate" && it.status == CheckStatus.FAILED })
    }

    @Test
    fun revokedSigner() = runTest {
        val signed = signer(options(AdesLevel.B_T), pki.revokedSigner).signEnveloped(xml)
        val result = validate(signed)
        assertFalse(result.isValid)
        assertEquals(Indication.INDETERMINATE, result.indication, result.describe())
        assertEquals(SubIndication.REVOKED_NO_POE, result.subIndication)
    }

    @Test
    fun expiredSigner() = runTest {
        val signed = signer(options(AdesLevel.B_B), pki.expiredSigner).signEnveloped(xml)
        val result = validate(signed)
        assertEquals(Indication.INDETERMINATE, result.indication, result.describe())
        assertTrue(result.subIndication in setOf(SubIndication.OUT_OF_BOUNDS_NO_POE, SubIndication.OUT_OF_BOUNDS_NOT_REVOKED), result.describe())
    }

    @Test
    fun brokenSignatureTimestamp() = runTest {
        val signed = sign(XadesPackaging.ENVELOPED, AdesLevel.B_T)
        val doc = Xml.parse(signed)
        val ts = XadesSignatureElement.findAll(doc).single().timestampContainers(TimestampContainerType.SIGNATURE).single()
        val foreign = tsa.timestamp(AdesDigestAlgorithm.SHA256, AdesDigestAlgorithm.SHA256.digest("something else".encodeToByteArray()))
        ts.element.child(XADES, "EncapsulatedTimeStamp")!!.textContent = Xml.b64(foreign)
        val broken = Xml.serialize(doc)

        val lenient = validate(broken)
        val s = lenient.signatures.single()
        assertTrue(s.timestamps.single().subIndication == SubIndication.HASH_FAILURE, lenient.describe())
        assertTrue(s.warnings.any { it.id.startsWith("timestamp:") }, lenient.describe())

        val strict = validate(broken, online.strict())
        assertEquals(Indication.TOTAL_FAILED, strict.indication, strict.describe())
        assertEquals(SubIndication.HASH_FAILURE, strict.subIndication)
    }

    @Test
    fun brokenArchiveTimestamp() = runTest {
        val signed = sign(XadesPackaging.ENVELOPED, AdesLevel.B_LTA)
        val doc = Xml.parse(signed)
        val sig = XadesSignatureElement.findAll(doc).single()
        // ds:Object elements added after the archive time-stamp break its imprint (EN 319 132-1 5.5.2.1 NOTE 5)
        sig.element.appendChild(doc.createElementNS(DS, "ds:Object")).textContent = "late addition"
        val strict = validate(Xml.serialize(doc), offline.strict())
        assertEquals(Indication.TOTAL_FAILED, strict.indication, strict.describe())
        assertEquals(SubIndication.HASH_FAILURE, strict.subIndication)
    }

    @Test
    fun rejectsDoctypeAndExternalEntities() = runTest {
        val xxe = """<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]><r>&x;</r>""".encodeToByteArray()
        val result = validate(xxe)
        assertEquals(Indication.TOTAL_FAILED, result.indication)
        assertTrue(result.documentChecks.any { it.id == "format:xml" })
        val error = runCatching { signer(options(AdesLevel.B_B)).signEnveloped(xxe) }.exceptionOrNull()
        assertTrue(error is XadesException, "$error")
    }

    @Test
    fun signatureWrappingWithDuplicateIdIsRejected() = runTest {
        val signed = sign(XadesPackaging.ENVELOPING, AdesLevel.B_B)
        val doc = Xml.parse(signed)
        val sig = XadesSignatureElement.findAll(doc).single()
        val original = sig.objects.first()
        // attacker moves the signed object away and injects a forged one with the same Id first in document order
        val forged = original.cloneNode(true) as Element
        forged.textContent = Xml.b64("forged".encodeToByteArray())
        sig.element.insertBefore(forged, sig.element.child(DS, "Object"))
        val result = validate(Xml.serialize(doc))
        assertEquals(Indication.TOTAL_FAILED, result.indication, result.describe())
        assertEquals(SubIndication.FORMAT_FAILURE, result.subIndication)
    }

    @Test
    fun plainXmlDsigIsNotXades() = runTest {
        val doc = Xml.parse(sign(XadesPackaging.ENVELOPED, AdesLevel.B_B))
        val sig = XadesSignatureElement.findAll(doc).single()
        sig.qualifyingPropertiesObject!!.let { it.parentNode.removeChild(it) }
        val result = validate(Xml.serialize(doc))
        assertEquals(SubIndication.FORMAT_FAILURE, result.subIndication, result.describe())
    }

    @Test
    fun rsaKeysAtLta() = runTest(timeout = 60.seconds) {
        val rsaPki = TestPki(keyType = TestPki.KeyType.RSA_2048)
        val options = XadesSignatureOptions(
            level = AdesLevel.B_LTA,
            augmentation = XadesAugmentationOptions(
                timestampSource = Rfc3161TimestampClient(rsaPki.tsaUrl, rsaPki.transport),
                transport = rsaPki.transport,
                trustAnchors = listOf(rsaPki.root.certificate),
            ),
        )
        val signed = XadesSigner(rsaPki.credentials(), options).signDetached(XadesDocument("a.txt", "hello".encodeToByteArray(), "text/plain"))
        val result = XadesValidator(AdesValidationPolicy(trustAnchors = listOf(rsaPki.root.certificate)))
            .validate(signed, listOf(XadesDocument("a.txt", "hello".encodeToByteArray())))
        assertValid(result, AdesLevel.B_LTA)
    }

    private fun AdesValidationPolicy.strict() = AdesValidationPolicy(
        trustAnchors = trustAnchors, validationTime = validationTime, transport = transport, strictTimestamps = true,
    )

    companion object {
        private val shared by lazy { TestPki() }
    }
}
