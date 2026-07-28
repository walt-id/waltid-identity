package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.*
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension
import id.walt.certificate.x509.extension.CrlDistributionPointsExtension.ReasonFlag
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.signum.SignumGeneralNameUtil.toGeneralNames
import id.walt.certificate.x509.signum.dn.toDistinguishedName
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName as SignumRdn


/**
 *  CRLDistributionPoints ::= SEQUENCE SIZE (1..MAX) OF DistributionPoint
 *
 *    DistributionPoint ::= SEQUENCE {
 *         distributionPoint       [0]     DistributionPointName OPTIONAL,
 *         reasons                 [1]     ReasonFlags OPTIONAL,
 *         cRLIssuer               [2]     GeneralNames OPTIONAL }
 *
 *    DistributionPointName ::= CHOICE {
 *         fullName                [0]     GeneralNames,
 *         nameRelativeToCRLIssuer [1]     RelativeDistinguishedName }
 *
 *    RelativeDistinguishedName ::= SET SIZE (1..MAX) OF AttributeTypeAndValue
 *
 */
class SignumCrlDistributionPointsExtension(extension: X509CertificateExtension) : SignumExtension(extension),
    CrlDistributionPointsExtension {

    override val distributionPoints: List<CrlDistributionPointsExtension.DistributionPoint>
        get() =
            extension.content.asSequence().children
                .map { parseDistributionPoint(it.asSequence()) }

    companion object {

        fun parseDistributionPoint(dp: Asn1Sequence): CrlDistributionPointsExtension.DistributionPoint {

            var distributionPointFullName: Collection<GeneralName>? = null
            var distributionPointNameRelativeToCrlIssuer: String? = null
            var reason: Set<ReasonFlag>? = null
            var cRLIssuer: Collection<GeneralName>? = null


            dp.children.filter {
                it.tag.tagClass == TagClass.CONTEXT_SPECIFIC
            }.forEach { dpSequenceElement ->
                when (dpSequenceElement.tag.tagValue) {
                    0uL -> {
                        // distributionPoint       [0]     DistributionPointName OPTIONAL,
                        dpSequenceElement.asStructure()
                            .children
                            .forEach {
                                if (it.tag.tagClass == TagClass.CONTEXT_SPECIFIC && it.tag.tagValue == 0uL) {
                                    // fullName                [0]     GeneralNames,
                                    require(distributionPointFullName == null) { "DistributionPointName can only be set once" }
                                    require(distributionPointNameRelativeToCrlIssuer == null) { "DistributionPointName can only be set once" }
                                    distributionPointFullName = it.asStructure().toGeneralNames()
                                } else if (it.tag.tagClass == TagClass.CONTEXT_SPECIFIC && it.tag.tagValue == 1uL) {
                                    // nameRelativeToCRLIssuer [1]     RelativeDistinguishedName
                                    require(distributionPointFullName == null) { "DistributionPointName can only be set once" }
                                    require(distributionPointNameRelativeToCrlIssuer == null) { "DistributionPointName can only be set once" }
                                    val rdnSet = it.withImplicitTag(Asn1Element.Tag.SET)
                                    val rdnParsed = SignumRdn.decodeFromDer(rdnSet.derEncoded)
                                    distributionPointNameRelativeToCrlIssuer =
                                        listOf(rdnParsed).toDistinguishedName().toString()
                                } else {
                                    throw IllegalArgumentException("Unknown tag ${it.tag} for DistributionPointName")
                                }
                            }
                    }

                    1uL -> {
                        // reasons [1] ReasonFlags OPTIONAL,
                        val bitString = Asn1BitString.decodeFromTlv(dpSequenceElement.asPrimitive())
                        val bitSet = bitString.toBitSet()
                        reason = decodeReasonFlags(bitSet)
                    }

                    2uL -> {
                        // cRLIssuer [2] GeneralNames OPTIONAL
                        TODO()
                    }
                }
            }
            return CrlDistributionPointsExtension.DistributionPoint(
                distributionPointFullName,
                distributionPointNameRelativeToCrlIssuer,
                reason,
                cRLIssuer
            )
        }

        fun decodeReasonFlags(value: BitSet): Set<ReasonFlag> {
            val reasonFlags = mutableSetOf<ReasonFlag>()
            //if (value[0]) {
            // unused
            //}
            if (value[1]) {
                reasonFlags.add(ReasonFlag.keyCompromise)
            }
            if (value[2]) {
                reasonFlags.add(ReasonFlag.cACompromise)
            }
            if (value[3]) {
                reasonFlags.add(ReasonFlag.affiliationChanged)
            }
            if (value[4]) {
                reasonFlags.add(ReasonFlag.superseded)
            }
            if (value[5]) {
                reasonFlags.add(ReasonFlag.cessationOfOperation)
            }
            if (value[6]) {
                reasonFlags.add(ReasonFlag.certificateHold)
            }
            if (value[7]) {
                reasonFlags.add(ReasonFlag.privilegeWithdrawn)
            }
            if (value[8]) {
                reasonFlags.add(ReasonFlag.aACompromise)
            }
            return reasonFlags
        }
    }


    fun createExtension(ext: CrlDistributionPointsExtension): Asn1PrimitiveOctetString {
        // aHR0cDovL2MucGtpLmdvb2cvd2UyL3lLNW5QaHRIS1FzLmNybA==
        TODO()
    }
}