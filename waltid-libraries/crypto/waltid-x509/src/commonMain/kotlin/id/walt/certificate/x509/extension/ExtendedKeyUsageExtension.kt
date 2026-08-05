package id.walt.certificate.x509.extension

interface ExtendedKeyUsageExtension : Extension {

    val keyPurposeIdList: Set<String>

    val keyPurposeList: Set<KeyUsage>
        get() = keyPurposeIdList.map { getKeyUsageById(it) }.toSet()

    enum class KeyUsage(val id: String) {
        anyExtendedKeyUsage("2.5.29.37.0"),
        serverAuth("1.3.6.1.5.5.7.3.1"),
        clientAuth("1.3.6.1.5.5.7.3.2"),
        codeSigning("1.3.6.1.5.5.7.3.3"),
        emailProtection("1.3.6.1.5.5.7.3.4"),
        ipsecEndSystem("1.3.6.1.5.5.7.3.5"),
        ipsecTunnel("1.3.6.1.5.5.7.3.6"),
        ipsecUser("1.3.6.1.5.5.7.3.7"),
        timeStamping("1.3.6.1.5.5.7.3.8"),
        OCSPSigning("1.3.6.1.5.5.7.3.9"),
        dvcs("1.3.6.1.5.5.7.3.10"),
        sbgpCertAAServerAuth("1.3.6.1.5.5.7.3.11"),
        scvpResponder("1.3.6.1.5.5.7.3.12"),
        eapOverPPP("1.3.6.1.5.5.7.3.13"),
        eapOverLAN("1.3.6.1.5.5.7.3.14"),
        scvpServer("1.3.6.1.5.5.7.3.15"),
        scvpClient("1.3.6.1.5.5.7.3.16"),
        ipsecIKE("1.3.6.1.5.5.7.3.17"),
        capwapAC("1.3.6.1.5.5.7.3.18"),
        capwapWTP("1.3.6.1.5.5.7.3.19"),
        cmcCA("1.3.6.1.5.5.7.3.27"),
        cmcRA("1.3.6.1.5.5.7.3.28"),
        cmKGA("1.3.6.1.5.5.7.3.32"),
        smartcardlogon("1.3.6.1.4.1.311.20.2.2"),
        macAddress("1.3.6.1.1.1.1.22"),
        msSGC("1.3.6.1.4.1.311.10.3.3"),
        nsSGC("2.16.840.1.113730.4.1"),
        mdlDS("1.0.18013.5.1.2"),
        unknown("")
    }

    companion object {

        const val OID = "2.5.29.37"
        const val NAME = "Extended Key Usage"

        fun MutableExtensionContainer.extensionExtendedKeyUsage(block: Builder.() -> Unit) {
            val builder = Builder(oid = OID)
            builder.block()
            this.extensions[OID] = builder
        }

        val ExtensionContainer.extensionExtendedKeyUsage: ExtendedKeyUsageExtension?
            get() {
                return this.extensions[OID] as? ExtendedKeyUsageExtension?
            }

        private val keyUsageById = KeyUsage.entries.associateBy { it.id }

        fun getKeyUsageById(id: String): KeyUsage =
            keyUsageById[id] ?: KeyUsage.unknown

    }

    data class Builder(
        override val oid: String = OID,
        override var critical: Boolean = false
    ) : ExtendedKeyUsageExtension {
        override val keyPurposeIdList: MutableSet<String> = mutableSetOf()

        fun addKeyUsage(vararg keyUsage: KeyUsage) {
            keyPurposeIdList.addAll(keyUsage.map { it.id })
        }
    }
}