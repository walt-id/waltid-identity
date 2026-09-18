package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.QcStatementsExtension

/**
 * QCStatements ::= SEQUENCE OF QCStatement
 * QCStatement ::= SEQUENCE { statementId OBJECT IDENTIFIER, statementInfo ANY DEFINED BY statementId OPTIONAL }
 */
class SignumQcStatementsExtension(extension: X509CertificateExtension) :
    SignumExtension(extension),
    QcStatementsExtension {

    override val statements: List<QcStatementsExtension.QcStatement>
        get() = extension.content.asSequence().children.map { statement ->
            val children = statement.asSequence().children.toList()
            val statementId = ObjectIdentifier.decodeFromTlv(children[0].asPrimitive()).toString()
            when (statementId) {
                QcStatementsExtension.QcStatement.ID_QC_COMPLIANCE -> QcStatementsExtension.QcStatement.QcCompliance
                QcStatementsExtension.QcStatement.ID_QC_SSCD -> QcStatementsExtension.QcStatement.QcSSCD
                QcStatementsExtension.QcStatement.ID_QC_TYPE -> {
                    val typeOids = children[1].asSequence().children.toList().map {
                        ObjectIdentifier.decodeFromTlv(it.asPrimitive()).toString()
                    }
                    QcStatementsExtension.QcStatement.QcType(typeOids)
                }

                else -> QcStatementsExtension.QcStatement.Other(statementId)
            }
        }

    companion object {
        fun createExtension(ext: QcStatementsExtension): Asn1EncapsulatingOctetString =
            Asn1.OctetStringEncapsulating {
                +Asn1.Sequence {
                    ext.statements.forEach { statement ->
                        +Asn1.Sequence {
                            +ObjectIdentifier(statement.statementId)
                            if (statement is QcStatementsExtension.QcStatement.QcType) {
                                +Asn1.Sequence {
                                    statement.typeOids.forEach { +ObjectIdentifier(it) }
                                }
                            }
                        }
                    }
                }
            }
    }
}
