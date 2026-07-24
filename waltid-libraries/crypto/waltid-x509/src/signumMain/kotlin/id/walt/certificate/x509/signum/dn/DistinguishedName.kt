package id.walt.certificate.x509.signum.dn

import id.walt.certificate.x509.dn.AttributeType
import id.walt.certificate.x509.dn.AttributeTypeAndValue
import id.walt.certificate.x509.dn.DistinguishedName
import at.asitplus.signum.indispensable.asn1.Asn1String as SignumAsn1String
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier as SignumOid
import at.asitplus.signum.indispensable.pki.AttributeTypeAndValue as SignumTypeAndValue
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName as SignumRdn

fun DistinguishedName.toSignumDn(): List<SignumRdn> =
    rdnList.map {
        SignumRdn(it.map { typeAndValue ->
            typeAndValue.toSignumTypeAndValue()
        })
    }

private fun AttributeTypeAndValue.toSignumTypeAndValue(): SignumTypeAndValue {

    val value: SignumAsn1String = when (this.type.defaultEncoding) {
        AttributeType.Encoding.printableString -> SignumAsn1String.Printable(this.value)
        AttributeType.Encoding.utf8String -> runCatching {
            SignumAsn1String.Printable(this.value)
        }.getOrElse { SignumAsn1String.UTF8(this.value) }
        else -> error("Unsupported attribute encoding ${this.type.defaultEncoding} for attribute '${this.type.name}' (${this.type.oid})")
    }
    return SignumTypeAndValue.Other(SignumOid(type.oid), value)
}

fun List<SignumRdn>.toDistinguishedName(): DistinguishedName =
    DistinguishedName(map { rdn ->
        rdn.attrsAndValues.map {
            val type = AttributeType.find(it.oid.toString())
            val stringValue = SignumAsn1String.decodeFromTlv(it.value.asPrimitive(), null)
            AttributeTypeAndValue(type, stringValue.value)
        }
    })
