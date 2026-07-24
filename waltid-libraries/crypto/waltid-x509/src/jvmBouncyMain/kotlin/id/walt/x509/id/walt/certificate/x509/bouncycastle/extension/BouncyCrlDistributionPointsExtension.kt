package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.CrlDistributionPointsExtension
import id.walt.certificate.x509.model.GeneralName
import id.walt.x509.id.walt.certificate.x509.bouncycastle.BouncyGeneralNameUtil.toBouncyCastleGeneralNames
import id.walt.x509.id.walt.certificate.x509.bouncycastle.BouncyGeneralNameUtil.toGeneralNamesList
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.x500.RDN
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.ReasonFlags.*
import org.bouncycastle.asn1.x509.DistributionPoint as BouncyCastleDistributionPoint
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension
import org.bouncycastle.asn1.x509.GeneralNames as BouncyCastleGeneralNames
import org.bouncycastle.asn1.x509.ReasonFlags as BouncyCastleReasonFlags

class BouncyCrlDistributionPointsExtension(extension: BouncyCastleExtension) :
    BouncyExtension(extension),
    CrlDistributionPointsExtension {

    override val distributionPoints: List<CrlDistributionPointsExtension.DistributionPoint>
        get() = CRLDistPoint.getInstance(extension.parsedValue)
            .distributionPoints.map { distributionPoint ->

                CrlDistributionPointsExtension.DistributionPoint(
                    distributionPointFullName = distributionPoint.distributionPoint?.fullName,
                    distributionPointNameRelativeToCrlIssuer = distributionPoint.distributionPoint.nameRelativeToCrlIssuer,
                    reason = distributionPoint.reasons?.toReasonFlags(),
                    cRLIssuer = distributionPoint.crlIssuer?.toGeneralNamesList(),
                )
            }

    companion object {

        private val DistributionPointName.fullName: List<GeneralName>?
            get() =
                if (type == DistributionPointName.FULL_NAME) {
                    BouncyCastleGeneralNames.getInstance(name)
                        .toGeneralNamesList()
                } else {
                    null
                }

        private val DistributionPointName.nameRelativeToCrlIssuer: String?
            get() =
                if (type == DistributionPointName.NAME_RELATIVE_TO_CRL_ISSUER) {
                    val rdn = RDN.getInstance(name)
                    X500Name(arrayOf(rdn)).toString()
                } else {
                    null
                }

        private fun CrlDistributionPointsExtension.DistributionPoint.getName(): DistributionPointName? {
            if (distributionPointFullName == null && distributionPointNameRelativeToCrlIssuer == null) {
                return null
            }
            if (distributionPointFullName != null) {
                require(distributionPointNameRelativeToCrlIssuer == null)
                return DistributionPointName(distributionPointFullName.toBouncyCastleGeneralNames())
            }
            require(distributionPointNameRelativeToCrlIssuer != null)
            val dn = X500Name(distributionPointNameRelativeToCrlIssuer)
            require(dn.size() == 1) { "Expected 'nameRelativeToCrlIssuer' to have one RND but it has ${dn.size()} ('${dn}')" }
            return DistributionPointName(DistributionPointName.NAME_RELATIVE_TO_CRL_ISSUER, dn.rdNs[0])
        }

        private fun CrlDistributionPointsExtension.DistributionPoint.getCrlIssuer(): BouncyCastleGeneralNames? =
            cRLIssuer?.toBouncyCastleGeneralNames()

        private fun Set<CrlDistributionPointsExtension.ReasonFlag>.toBouncyReasonFlags(): BouncyCastleReasonFlags? {
            var flagsValue = 0
            this.forEach {
                when (it) {
                    CrlDistributionPointsExtension.ReasonFlag.keyCompromise ->
                        flagsValue = flagsValue or BouncyCastleReasonFlags.keyCompromise

                    CrlDistributionPointsExtension.ReasonFlag.cACompromise ->
                        flagsValue = flagsValue or BouncyCastleReasonFlags.cACompromise

                    CrlDistributionPointsExtension.ReasonFlag.affiliationChanged ->
                        flagsValue = flagsValue or BouncyCastleReasonFlags.affiliationChanged

                    CrlDistributionPointsExtension.ReasonFlag.superseded ->
                        flagsValue = flagsValue or BouncyCastleReasonFlags.superseded

                    CrlDistributionPointsExtension.ReasonFlag.cessationOfOperation ->
                        flagsValue = flagsValue or BouncyCastleReasonFlags.cessationOfOperation

                    CrlDistributionPointsExtension.ReasonFlag.certificateHold ->
                        flagsValue = flagsValue or BouncyCastleReasonFlags.certificateHold

                    CrlDistributionPointsExtension.ReasonFlag.privilegeWithdrawn ->
                        flagsValue = flagsValue or BouncyCastleReasonFlags.privilegeWithdrawn

                    CrlDistributionPointsExtension.ReasonFlag.aACompromise ->
                        flagsValue = flagsValue or BouncyCastleReasonFlags.aACompromise
                }
            }
            return BouncyCastleReasonFlags(flagsValue)
        }

        private fun BouncyCastleReasonFlags.hasReason(reason: Int): Boolean =
            this.intValue() and reason == reason

        private fun BouncyCastleReasonFlags.toReasonFlags(): Set<CrlDistributionPointsExtension.ReasonFlag>? {
            val result = mutableSetOf<CrlDistributionPointsExtension.ReasonFlag>()
            if (hasReason(keyCompromise)) {
                result.add(CrlDistributionPointsExtension.ReasonFlag.keyCompromise)
            }
            if (hasReason(cACompromise)) {
                result.add(CrlDistributionPointsExtension.ReasonFlag.cACompromise)
            }
            if (hasReason(affiliationChanged)) {
                result.add(CrlDistributionPointsExtension.ReasonFlag.affiliationChanged)
            }
            if (hasReason(superseded)) {
                result.add(CrlDistributionPointsExtension.ReasonFlag.superseded)
            }
            if (hasReason(cessationOfOperation)) {
                result.add(CrlDistributionPointsExtension.ReasonFlag.cessationOfOperation)
            }
            if (hasReason(certificateHold)) {
                result.add(CrlDistributionPointsExtension.ReasonFlag.certificateHold)
            }
            if (hasReason(privilegeWithdrawn)) {
                result.add(CrlDistributionPointsExtension.ReasonFlag.privilegeWithdrawn)
            }
            if (hasReason(aACompromise)) {
                result.add(CrlDistributionPointsExtension.ReasonFlag.aACompromise)
            }
            return if (result.isEmpty()) {
                null
            } else {
                result.toSet()
            }
        }

        fun createExtension(ext: CrlDistributionPointsExtension): ASN1Object {
            val bouncyDistributionPoints = ext.distributionPoints.map { distributionPoint ->
                val point = BouncyCastleDistributionPoint(
                    distributionPoint.getName(),
                    distributionPoint.reason?.toBouncyReasonFlags(),
                    distributionPoint.getCrlIssuer()
                )
                require(
                    point.distributionPoint != null || point.crlIssuer != null
                )
                { "DistributionPoint or crlIssuer must be set" }
                point
            }.toTypedArray()
            return CRLDistPoint(bouncyDistributionPoints)
        }
    }
}