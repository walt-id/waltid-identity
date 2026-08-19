package id.walt.certificate.x509.extension

/**
 * KeyUsage ::= BIT STRING {
 *            digitalSignature        (0),
 *            nonRepudiation          (1), -- recent editions of X.509 have
 *                                 -- renamed this bit to contentCommitment
 *            keyEncipherment         (2),
 *            dataEncipherment        (3),
 *            keyAgreement            (4),
 *            keyCertSign             (5),
 *            cRLSign                 (6),
 *            encipherOnly            (7),
 *            decipherOnly            (8) }
 */
interface KeyUsageExtension : Extension {

    val keyPurposeIdList: Set<KeyUsage>

    enum class KeyUsage {
        digitalSignature,
        nonRepudiation,
        keyEncipherment,
        dataEncipherment,
        keyAgreement,
        keyCertSign,
        cRLSign,
        encipherOnly,
        decipherOnly
    }

    companion object {

        const val OID = "2.5.29.15"
        const val NAME = "Key Usage"

        fun MutableExtensionContainer.extensionKeyUsage(block: Builder.() -> Unit) {
            val builder = Builder()
            builder.block()
            this.extensions[OID] = builder
        }

        val ExtensionContainer.extensionKeyUsage: KeyUsageExtension?
            get() {
                return this.extensions[OID] as? KeyUsageExtension?
            }

    }

    data class Builder(
        override var critical: Boolean = false,
    ) : KeyUsageExtension {
        override val oid: String = OID
        override val keyPurposeIdList: MutableSet<KeyUsage> = mutableSetOf()

        fun addKeyUsage(vararg keyUsage: KeyUsage) {
            keyPurposeIdList.addAll(keyUsage)
        }
    }
}