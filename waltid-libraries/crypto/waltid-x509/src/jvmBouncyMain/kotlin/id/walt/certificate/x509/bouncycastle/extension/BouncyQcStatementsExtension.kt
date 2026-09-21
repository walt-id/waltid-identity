package id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.QcStatementsExtension
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x509.qualified.QCStatement
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension

class BouncyQcStatementsExtension(extension: BouncyCastleExtension) : BouncyExtension(extension),
    QcStatementsExtension {

    override val statements: List<QcStatementsExtension.QcStatement>
        get() = ASN1Sequence.getInstance(extension.parsedValue).map { element ->
            val statement = QCStatement.getInstance(element)
            when (statement.statementId.id) {
                QcStatementsExtension.QcStatement.ID_QC_COMPLIANCE -> QcStatementsExtension.QcStatement.QcCompliance
                QcStatementsExtension.QcStatement.ID_QC_SSCD -> QcStatementsExtension.QcStatement.QcSSCD
                QcStatementsExtension.QcStatement.ID_QC_TYPE -> {
                    val typeOids = ASN1Sequence.getInstance(statement.statementInfo)
                        .map { ASN1ObjectIdentifier.getInstance(it).id }
                    QcStatementsExtension.QcStatement.QcType(typeOids)
                }

                else -> QcStatementsExtension.QcStatement.Other(statement.statementId.id)
            }
        }

    companion object {
        fun createExtension(ext: QcStatementsExtension): ASN1Object {
            val statements = ext.statements.map { statement ->
                val statementId = ASN1ObjectIdentifier(statement.statementId)
                val statementInfo: ASN1Encodable? = when (statement) {
                    is QcStatementsExtension.QcStatement.QcType ->
                        DERSequence(statement.typeOids.map { ASN1ObjectIdentifier(it) }.toTypedArray())

                    else -> null
                }
                if (statementInfo != null) {
                    QCStatement(statementId, statementInfo)
                } else {
                    QCStatement(statementId)
                }
            }.toTypedArray()
            return DERSequence(statements)
        }
    }
}
