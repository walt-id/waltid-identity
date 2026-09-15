package id.walt.certificate.x509.extension

/**
 * RFC 3739 / ETSI EN 319 412-5 qcStatements extension.
 *
 * QCStatements ::= SEQUENCE OF QCStatement
 *
 * QCStatement ::= SEQUENCE {
 *      statementId   OBJECT IDENTIFIER,
 *      statementInfo ANY DEFINED BY statementId OPTIONAL }
 *
 * Only the statement shapes actually needed by the ETSI TS 119 412-6 / 119 411-8 profiles are
 * modelled explicitly (QcCompliance, QcSSCD, QcType); [QcStatement.Other] is a fallback that
 * preserves any other statement's id (without its statementInfo) so parsing doesn't lose data.
 */
interface QcStatementsExtension : Extension {

    val statements: List<QcStatement>

    sealed interface QcStatement {
        val statementId: String

        /** esi4-qcStatement-1 (0.4.0.1862.1.1) - certificate is a qualified certificate. */
        data object QcCompliance : QcStatement {
            override val statementId: String = ID_QC_COMPLIANCE
        }

        /** esi4-qcStatement-4 (0.4.0.1862.1.4) - private key resides in a QSCD. */
        data object QcSSCD : QcStatement {
            override val statementId: String = ID_QC_SSCD
        }

        /**
         * esi4-qcStatement-6 (0.4.0.1862.1.6) - statementInfo is a SEQUENCE OF OBJECT IDENTIFIER
         * naming the certificate type(s), e.g. id-etsi-qct-esign/eseal/web, or the EUDI-specific
         * id-etsi-qct-pid / id-etsi-qct-wal.
         */
        data class QcType(val typeOids: List<String>) : QcStatement {
            override val statementId: String = ID_QC_TYPE
        }

        /** Any statement this model doesn't represent explicitly; statementInfo is discarded. */
        data class Other(override val statementId: String) : QcStatement

        companion object {
            const val ID_QC_COMPLIANCE = "0.4.0.1862.1.1"
            const val ID_QC_SSCD = "0.4.0.1862.1.4"
            const val ID_QC_TYPE = "0.4.0.1862.1.6"
        }
    }

    companion object {

        const val OID = "1.3.6.1.5.5.7.1.3"
        const val NAME = "Qualified Certificate Statements"

        fun MutableExtensionContainer.extensionQcStatements(block: Builder.() -> Unit) {
            val builder = Builder()
            builder.block()
            this.extensions[OID] = builder
        }

        val ExtensionContainer.extensionQcStatements: QcStatementsExtension?
            get() {
                return this.extensions[OID] as? QcStatementsExtension?
            }
    }

    data class Builder(
        override var critical: Boolean = false,
    ) : QcStatementsExtension {
        override val oid: String = OID
        override val statements: MutableList<QcStatement> = mutableListOf()

        fun addQcCompliance() {
            statements.add(QcStatement.QcCompliance)
        }

        fun addQcSscd() {
            statements.add(QcStatement.QcSSCD)
        }

        fun addQcType(vararg typeOids: String) {
            statements.add(QcStatement.QcType(typeOids.toList()))
        }

        fun addStatement(statementId: String) {
            statements.add(QcStatement.Other(statementId))
        }
    }
}
