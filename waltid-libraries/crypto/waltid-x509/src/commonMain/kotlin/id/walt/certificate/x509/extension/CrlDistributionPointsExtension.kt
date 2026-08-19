package id.walt.certificate.x509.extension

import id.walt.certificate.x509.model.GeneralName

interface CrlDistributionPointsExtension : Extension {

    val distributionPoints: List<DistributionPoint>

    enum class ReasonFlag {
        keyCompromise,
        cACompromise,
        affiliationChanged,
        superseded,
        cessationOfOperation,
        certificateHold,
        privilegeWithdrawn,
        aACompromise
    }

    data class DistributionPoint(
        val distributionPointFullName: Collection<GeneralName>?,
        val distributionPointNameRelativeToCrlIssuer: String?,
        val reason: Set<ReasonFlag>?,
        val cRLIssuer: Collection<GeneralName>?
    )

    companion object {
        const val OID = "2.5.29.31"
        const val NAME = "CRL Distribution Points"


        fun MutableExtensionContainer.extensionCrlDistributionPoints(block: Builder.() -> Unit) {
            val builder = Builder()
            builder.block()
            this.extensions[OID] = builder
        }

        val ExtensionContainer.extensionCrlDistributionPoints: CrlDistributionPointsExtension?
            get() {
                return this.extensions[OID] as? CrlDistributionPointsExtension?
            }
    }

    data class Builder(
        override var critical: Boolean = false
    ) : CrlDistributionPointsExtension {
        override val oid: String = OID
        override val distributionPoints: MutableList<DistributionPoint> = mutableListOf()

        fun addUriDistributionPoint(
            distributionPointUri: String,
            reason: Set<ReasonFlag>? = null,
            crlIssuer: List<GeneralName>? = null
        ) =
            addUriDistributionPoint(
                listOf(distributionPointUri),
                reason,
                crlIssuer
            )

        fun addUriDistributionPoint(
            distributionPointUris: Collection<String>,
            reason: Set<ReasonFlag>? = null,
            crlIssuer: List<GeneralName>? = null
        ) =
            addDistributionPointFullName(
                distributionPointUris.map {
                    GeneralName(
                        GeneralName.NameType.uniformResourceIdentifier,
                        it
                    )
                },
                reason,
                crlIssuer
            )

        fun addDistributionPointFullName(
            distributionPointNames: Collection<GeneralName>,
            reason: Set<ReasonFlag>? = null,
            crlIssuer: List<GeneralName>? = null
        ) {
            addDistributionPoint(
                DistributionPoint(
                    distributionPointFullName = distributionPointNames.toList(),
                    distributionPointNameRelativeToCrlIssuer = null,
                    reason = reason,
                    cRLIssuer = crlIssuer
                )
            )
        }

        fun addDistributionPointRelativeName(
            nameRelativeToCrlIssuer: String,
            reason: Set<ReasonFlag>? = null,
            crlIssuer: Collection<GeneralName>? = null
        ) {
            addDistributionPoint(
                DistributionPoint(
                    distributionPointFullName = null,
                    distributionPointNameRelativeToCrlIssuer = nameRelativeToCrlIssuer,
                    reason = reason,
                    cRLIssuer = crlIssuer
                )
            )
        }

        fun addDistributionPoint(distributionPoint: DistributionPoint) {
            distributionPoint.cRLIssuer?.also {
                require(it.all { gn -> gn.type == GeneralName.NameType.directoryName }) {
                    "crlIssuer must be a directory"
                }
            }
            distributionPoints.add(distributionPoint)
        }
    }
}