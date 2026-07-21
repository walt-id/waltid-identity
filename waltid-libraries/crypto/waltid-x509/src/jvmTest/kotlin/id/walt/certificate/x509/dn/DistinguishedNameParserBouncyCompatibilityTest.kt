package id.walt.certificate.x509.dn

import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class DistinguishedNameParserBouncyCompatibilityTest {

    @CsvSource(
        value = [
            "cn = walt.id "
        ]
    )
    @ParameterizedTest
    fun shouldParseDnStringSameAsBouncyCastleImplementation(dn: String) {
        val bouncy = X500Name(dn)
        val parsedDn = DistinguishedName.ofString(dn)

        assertEquals(bouncy.size(), parsedDn.rdnList.size, "rdn list size not match")
        bouncy.rdNs.forEachIndexed { index, bouncyRdn ->
            val parsedRdn = parsedDn.rdnList[index]
            assertEquals(bouncyRdn.size(), parsedRdn.size, "rdn list #${index} size not match")
            bouncyRdn.typesAndValues.forEachIndexed { valueIndex, bouncyTypeAndValue ->
                val typeAndValue = parsedRdn[valueIndex]
                assertEquals(bouncyTypeAndValue.type.id, typeAndValue.type.oid)
                assertEquals(bouncyTypeAndValue.value.toASN1Primitive().toString(), typeAndValue.value)
            }
        }
    }
}