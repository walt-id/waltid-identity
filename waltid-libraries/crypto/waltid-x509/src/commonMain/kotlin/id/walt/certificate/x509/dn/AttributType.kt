package id.walt.certificate.x509.dn

data class AttributeType(
    /**
     * must match regex ^\d+(\.\d+)*$
     */
    val oid: String,

    val defaultEncoding: Encoding,

    /*
     * Short Name is defined in RFC4512 (Section 1.4. Common ABNF Productions) with ABNF:
     * oid = descr / numericoid
     * descr = keystring
     * keystring = leadkeychar *keychar
     * leadkeychar = ALPHA
     * keychar = ALPHA / DIGIT / HYPHEN
     *
     * A name must match regex ^[a-z][a-z0-9\-]*$
     */
    val names: Collection<String>
) {

    enum class Encoding {
        directoryString,
        printableString,
        ia5String,
        generalizedTime,
        notSupported
    }

    val name: String = names.first()

    companion object {
        val knownTypes = listOf(
            // 0.9.2342.19200300.100.1
            AttributeType("0.9.2342.19200300.100.1.1", Encoding.directoryString, listOf("uid")),
            AttributeType("0.9.2342.19200300.100.1.3", Encoding.ia5String, listOf("mail")),
            AttributeType("0.9.2342.19200300.100.1.25", Encoding.ia5String, listOf("dc", "domainComponent")),

            //1.2.840.113549.1.9
            AttributeType("1.2.840.113549.1.9.1", Encoding.ia5String, listOf("emailAddress", "e")),
            AttributeType("1.2.840.113549.1.9.2", Encoding.ia5String, listOf("unstructuredName")),
            AttributeType("1.2.840.113549.1.9.8", Encoding.directoryString, listOf("unstructuredAddress")),

            //1.3.6.1.4.1.311.60.2.1
            AttributeType("1.3.6.1.4.1.311.60.2.1.1", Encoding.notSupported, listOf("jurisdictionLocality")),
            AttributeType("1.3.6.1.4.1.311.60.2.1.2", Encoding.notSupported, listOf("jurisdictionState")),
            AttributeType("1.3.6.1.4.1.311.60.2.1.3", Encoding.printableString, listOf("jurisdictionCountry")),

            //1.3.6.1.5.5.7.9
            AttributeType("1.3.6.1.5.5.7.9.1", Encoding.generalizedTime, listOf("dateOfBirth")),
            AttributeType("1.3.6.1.5.5.7.9.2", Encoding.notSupported, listOf("placeOfBirth")),
            AttributeType("1.3.6.1.5.5.7.9.3", Encoding.notSupported, listOf("gender")),
            AttributeType("1.3.6.1.5.5.7.9.4", Encoding.notSupported, listOf("countryOfCitizenship")),
            AttributeType("1.3.6.1.5.5.7.9.5", Encoding.notSupported, listOf("countryOfResidence")),

            AttributeType("1.3.36.8.3.14", Encoding.notSupported, listOf("nameAtBirth")),

            //2.5.4
            AttributeType("2.5.4.3", Encoding.notSupported, listOf("cn", "commonName")),
            AttributeType("2.5.4.4", Encoding.notSupported, listOf("surname")),
            AttributeType("2.5.4.5", Encoding.printableString, listOf("serialNumber")),
            AttributeType("2.5.4.6", Encoding.printableString, listOf("c", "countryName")),
            AttributeType("2.5.4.7", Encoding.notSupported, listOf("l", "localityName")),
            AttributeType("2.5.4.8", Encoding.notSupported, listOf("st", "stateOrProvinceName")),
            AttributeType("2.5.4.9", Encoding.notSupported, listOf("street", "streetAddress")),
            AttributeType("2.5.4.10", Encoding.notSupported, listOf("o", "organizationName")),
            AttributeType("2.5.4.11", Encoding.notSupported, listOf("ou", "organizationalUnitName")),
            AttributeType("2.5.4.12", Encoding.notSupported, listOf("t", "title")),
            AttributeType("2.5.4.13", Encoding.notSupported, listOf("description")),
            AttributeType("2.5.4.15", Encoding.notSupported, listOf("businessCategory")),
            AttributeType("2.5.4.16", Encoding.notSupported, listOf("postalAddress")),
            AttributeType("2.5.4.17", Encoding.notSupported, listOf("postalCode")),
            AttributeType("2.5.4.20", Encoding.printableString, listOf("telephoneNumber")),
            AttributeType("2.5.4.41", Encoding.notSupported, listOf("name")),
            AttributeType("2.5.4.42", Encoding.notSupported, listOf("givenName")),
            AttributeType("2.5.4.43", Encoding.notSupported, listOf("initials")),
            AttributeType("2.5.4.44", Encoding.notSupported, listOf("generationQualifier", "generation")),
            AttributeType("2.5.4.45", Encoding.notSupported, listOf("uniqueIdentifier", "x500UniqueIdentifier")),
            AttributeType("2.5.4.46", Encoding.printableString, listOf("dn", "dnQualifier")),
            AttributeType("2.5.4.51", Encoding.notSupported, listOf("houseIdentifier")),
            AttributeType("2.5.4.54", Encoding.notSupported, listOf("dmdName")),
            AttributeType("2.5.4.65", Encoding.notSupported, listOf("pseudonym")),
            AttributeType("2.5.4.72", Encoding.notSupported, listOf("role")),
            AttributeType("2.5.4.77", Encoding.notSupported, listOf("uuidpair")),
            AttributeType("2.5.4.83", Encoding.notSupported, listOf("uri")),
            AttributeType("2.5.4.86", Encoding.notSupported, listOf("urn")),
            AttributeType("2.5.4.87", Encoding.notSupported, listOf("url")),
            AttributeType("2.5.4.97", Encoding.notSupported, listOf("organizationIdentifier")),
            AttributeType("2.5.4.98", Encoding.notSupported, listOf("c3", "countryCode3c")),
            AttributeType("2.5.4.99", Encoding.notSupported, listOf("n3", "countryCode3n")),
            AttributeType("2.5.4.100", Encoding.notSupported, listOf("dnsName")),
            AttributeType("2.5.4.106", Encoding.notSupported, listOf("objectIdentifier")),

            // to be extended if required
        )

        val map = knownTypes.flatMap { type -> (type.names + type.oid).map { name -> Pair(name, type) } }
            .associateBy({ it.first.lowercase() }, { it.second })

        fun find(name: String) = map.getValue(name.lowercase())

    }
}