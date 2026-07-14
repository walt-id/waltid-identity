package id.walt.certificate.x509.extension

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
            val builder = Builder(oid = OID)
            builder.block()
            this.extensions[OID] = builder
        }

        val ExtensionContainer.extensionKeyUsage: KeyUsageExtension?
            get() {
                return this.extensions[OID] as? KeyUsageExtension?
            }

    }

    data class Builder(
        override val oid: String = OID,
        override var critical: Boolean = false,
    ) : KeyUsageExtension {
        override val keyPurposeIdList: MutableSet<KeyUsage> = mutableSetOf()

        fun addKeyUsage(vararg keyUsage: KeyUsage) {
            keyPurposeIdList.addAll(keyUsage)
        }
    }
}