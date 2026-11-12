package id.walt.ades.pades

import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.crypto.AdesSigningCredentials
import id.walt.ades.model.Commitment
import id.walt.ades.model.CommitmentType
import id.walt.ades.model.SignaturePolicyIdentifier
import id.walt.ades.tsp.TimeStampToken
import id.walt.ades.tsp.TimestampException
import id.walt.ades.tsp.TimestampSource
import id.walt.ades.x509.X509Certificate
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.esf.CommitmentTypeIndication
import org.bouncycastle.asn1.esf.OtherHashAlgAndValue
import org.bouncycastle.asn1.esf.SigPolicyQualifierInfo
import org.bouncycastle.asn1.esf.SigPolicyQualifiers
import org.bouncycastle.asn1.esf.SignaturePolicyId
import org.bouncycastle.asn1.esf.SignaturePolicyIdentifier as BcSignaturePolicyIdentifier
import org.bouncycastle.asn1.ess.ESSCertIDv2
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.IssuerSerial
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cms.CMSAttributeTableGenerator
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignatureEncryptionAlgorithmFinder
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.SignerInfoGeneratorBuilder
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.SignerInformationStore
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider
import org.bouncycastle.util.CollectionStore
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Builds the CAdES (ETSI.CAdES.detached) SignedData of a PAdES signature with BouncyCastle. */
internal object PadesCms {

    /**
     * BC ContentSigner that only buffers the DER signed attributes. The suspend crypto2 signer cannot be called from
     * BC's blocking callback, so generation runs twice: once to capture the signed attributes, once with the
     * signature value produced by [AdesSigningCredentials.sign]. The signed attributes are deterministic (no
     * signing-time), which is verified.
     */
    private class BufferingContentSigner(
        private val algorithm: AlgorithmIdentifier,
        private val signature: ByteArray?,
        private val expectedInput: ByteArray?,
    ) : ContentSigner {
        val buffer = ByteArrayOutputStream()
        override fun getAlgorithmIdentifier(): AlgorithmIdentifier = algorithm
        override fun getOutputStream(): OutputStream = buffer
        override fun getSignature(): ByteArray {
            if (expectedInput != null) check(expectedInput.contentEquals(buffer.toByteArray())) { "Signed attributes changed between passes" }
            return signature ?: ByteArray(0)
        }
    }

    suspend fun sign(
        content: ByteArray,
        credentials: AdesSigningCredentials,
        signaturePolicy: SignaturePolicyIdentifier?,
        commitment: Commitment?,
        timestampSource: TimestampSource?,
        timestampDigest: AdesDigestAlgorithm,
        /** Certificate referenced by signing-certificate-v2; differs from the signer only in negative tests. */
        referencedCertificate: X509Certificate = credentials.signingCertificate,
    ): ByteArray {
        val algorithm = AlgorithmIdentifier.getInstance(credentials.algorithm.algorithmIdentifier())
        val essDigest = credentials.algorithm.digest?.takeIf { it != AdesDigestAlgorithm.SHA1 } ?: AdesDigestAlgorithm.SHA256
        val extraAttributes = buildList {
            add(signingCertificateV2(referencedCertificate, essDigest))
            signaturePolicy?.let { add(signaturePolicyAttribute(it)) }
            commitment?.let { add(commitmentAttribute(it)) }
        }

        val capture = BufferingContentSigner(algorithm, null, null)
        generate(content, credentials, capture, extraAttributes)
        val toBeSigned = capture.buffer.toByteArray()
        val signatureValue = credentials.sign(toBeSigned, derEcdsa = true)
        var cms = generate(content, credentials, BufferingContentSigner(algorithm, signatureValue, toBeSigned), extraAttributes)

        if (timestampSource != null) cms = addSignatureTimestamp(cms, timestampSource, timestampDigest)
        return cms.getEncoded(ASN1Encoding.DER)
    }

    private fun generate(content: ByteArray, credentials: AdesSigningCredentials, signer: ContentSigner, extra: List<Attribute>): CMSSignedData {
        val signedAttributes = CMSAttributeTableGenerator { params ->
            val v = ASN1EncodableVector()
            v.add(Attribute(CMSAttributes.contentType, DERSet(params[CMSAttributeTableGenerator.CONTENT_TYPE] as ASN1ObjectIdentifier)))
            v.add(Attribute(CMSAttributes.messageDigest, DERSet(DEROctetString(params[CMSAttributeTableGenerator.DIGEST] as ByteArray))))
            extra.forEach(v::add)
            AttributeTable(v)
        }
        val signerInfoGenerator = SignerInfoGeneratorBuilder(BcDigestCalculatorProvider(), CMSSignatureEncryptionAlgorithmFinder { it })
            .setSignedAttributeGenerator(signedAttributes)
            .build(signer, X509CertificateHolder(credentials.signingCertificate.encoded))
        val generator = CMSSignedDataGenerator()
        generator.addSignerInfoGenerator(signerInfoGenerator)
        generator.addCertificates(CollectionStore(credentials.certificateChain.distinct().map { X509CertificateHolder(it.encoded) }))
        return generator.generate(CMSProcessableByteArray(content), false)
    }

    /** Adds a signature-time-stamp (EN 319 122-1 5.3) over the SignerInfo signature value. */
    suspend fun addSignatureTimestamp(cms: CMSSignedData, source: TimestampSource, digest: AdesDigestAlgorithm): CMSSignedData {
        val signer = cms.signerInfos.signers.single()
        val imprint = digest.digest(signer.signature)
        val token = source.timestamp(digest, imprint)
        if (!TimeStampToken(token).tstInfo.messageImprint.contentEquals(imprint)) throw TimestampException("Time-stamp token does not cover the signature value")
        val attribute = Attribute(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken, DERSet(ASN1Primitive.fromByteArray(token)))
        val unsigned = signer.unsignedAttributes?.toASN1EncodableVector() ?: ASN1EncodableVector()
        unsigned.add(attribute)
        val updated = SignerInformation.replaceUnsignedAttributes(signer, AttributeTable(unsigned))
        return CMSSignedData.replaceSigners(cms, SignerInformationStore(updated))
    }

    private suspend fun signingCertificateV2(certificate: X509Certificate, digest: AdesDigestAlgorithm): Attribute {
        val holder = X509CertificateHolder(certificate.encoded)
        val issuerSerial = IssuerSerial(GeneralNames(GeneralName(holder.issuer)), holder.serialNumber)
        val certId = ESSCertIDv2(AlgorithmIdentifier(ASN1ObjectIdentifier(digest.oid)), digest.digest(certificate.encoded), issuerSerial)
        return Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2, DERSet(SigningCertificateV2(arrayOf(certId))))
    }

    private fun signaturePolicyAttribute(policy: SignaturePolicyIdentifier): Attribute {
        val qualifiers = policy.uri?.let { SigPolicyQualifiers(arrayOf(SigPolicyQualifierInfo(PKCSObjectIdentifiers.id_spq_ets_uri, DERIA5String(it)))) }
        val id = SignaturePolicyId(
            ASN1ObjectIdentifier(policy.id.removePrefix("urn:oid:")),
            OtherHashAlgAndValue(AlgorithmIdentifier(ASN1ObjectIdentifier(policy.digestAlgorithm.oid)), DEROctetString(policy.digest)),
            qualifiers,
        )
        return Attribute(PKCSObjectIdentifiers.id_aa_ets_sigPolicyId, DERSet(BcSignaturePolicyIdentifier(id) as ASN1Encodable))
    }

    private fun commitmentAttribute(commitment: Commitment): Attribute {
        val oid = CommitmentType.fromUri(commitment.id)?.oid ?: commitment.id.removePrefix("urn:oid:")
        require(oid.matches(Regex("[0-2](\\.\\d+)+"))) { "PAdES commitment-type-indication needs an OID or a TS 119 172-1 URI: ${commitment.id}" }
        return Attribute(PKCSObjectIdentifiers.id_aa_ets_commitmentType, DERSet(CommitmentTypeIndication(ASN1ObjectIdentifier(oid))))
    }
}
