package id.walt.certificate.x509.model

data class GeneralName(
    val type: NameType,
    val value: String
) {

    enum class NameType {
        dNSName,
        uniformResourceIdentifier,
        IPAddress,
        registeredID,
        directoryName,
        rfc822Name,
        ediPartyName,
        x400Address,
        otherName
    }
}