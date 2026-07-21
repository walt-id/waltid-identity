package id.walt.x509.id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.model.GeneralName
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import java.net.InetAddress
import org.bouncycastle.asn1.x509.GeneralName as BouncyCastleGeneralName
import org.bouncycastle.asn1.x509.GeneralNames as BouncyCastleGeneralNames


internal object BouncyGeneralNameUtil {

    fun BouncyCastleGeneralName.toGeneralName(): GeneralName =
        when (tagNo) {
            BouncyCastleGeneralName.dNSName -> GeneralName(
                GeneralName.NameType.dNSName,
                name.toString()
            )

            BouncyCastleGeneralName.rfc822Name -> GeneralName(
                GeneralName.NameType.rfc822Name,
                name.toString()
            )

            BouncyCastleGeneralName.uniformResourceIdentifier -> GeneralName(
                GeneralName.NameType.uniformResourceIdentifier,
                name.toString()
            )

            BouncyCastleGeneralName.iPAddress -> {
                val ipBytes: ByteArray? = (name as ASN1OctetString).octets
                val address = InetAddress.getByAddress(ipBytes)
                GeneralName(
                    GeneralName.NameType.IPAddress,
                    address.hostAddress
                )
            }

            BouncyCastleGeneralName.registeredID -> {
                GeneralName(
                    GeneralName.NameType.registeredID,
                    (name as ASN1ObjectIdentifier).id
                )
            }

            else -> GeneralName(
                GeneralName.NameType.otherName,
                "Alternative Name Type '${tagNo}' is not implemented"
            )
        }

    fun Array<BouncyCastleGeneralName>.toGeneralNamesList(): List<GeneralName> =
        this.map {
            it.toGeneralName()
        }

    fun BouncyCastleGeneralNames.toGeneralNamesList(): List<GeneralName> =
        this.names.toGeneralNamesList()


    fun GeneralName.toBouncyCastleGeneralName(): BouncyCastleGeneralName =
        when (type) {
            GeneralName.NameType.dNSName -> BouncyCastleGeneralName(
                BouncyCastleGeneralName.dNSName,
                value
            )

            GeneralName.NameType.uniformResourceIdentifier -> BouncyCastleGeneralName(
                BouncyCastleGeneralName.uniformResourceIdentifier,
                value
            )

            GeneralName.NameType.IPAddress -> BouncyCastleGeneralName(
                BouncyCastleGeneralName.iPAddress,
                value
            )

            GeneralName.NameType.registeredID -> BouncyCastleGeneralName(
                BouncyCastleGeneralName.registeredID,
                value
            )

            GeneralName.NameType.directoryName -> BouncyCastleGeneralName(
                BouncyCastleGeneralName.directoryName,
                value
            )

            GeneralName.NameType.rfc822Name -> BouncyCastleGeneralName(
                BouncyCastleGeneralName.rfc822Name,
                value
            )

            GeneralName.NameType.ediPartyName -> BouncyCastleGeneralName(
                BouncyCastleGeneralName.ediPartyName,
                value
            )

            GeneralName.NameType.x400Address -> BouncyCastleGeneralName(
                BouncyCastleGeneralName.x400Address,
                value
            )

            GeneralName.NameType.otherName -> BouncyCastleGeneralName(
                BouncyCastleGeneralName.otherName,
                value
            )
        }


    fun Collection<GeneralName>.toBouncyCastleGeneralNames(): BouncyCastleGeneralNames =
        this.map { it.toBouncyCastleGeneralName() }
            .toTypedArray()
            .let { BouncyCastleGeneralNames(it) }
}