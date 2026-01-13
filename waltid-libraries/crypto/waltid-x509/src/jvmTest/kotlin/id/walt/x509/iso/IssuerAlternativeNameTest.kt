package id.walt.x509.iso

import org.bouncycastle.asn1.x509.GeneralName
import kotlin.test.Test
import kotlin.test.assertEquals


class IssuerAlternativeNameTest {

    @Test
    fun issuerAlternativeNameToGeneralNameArrayHandlesBothEntries() {
        val ian = IssuerAlternativeName(
            uri = "https://example.com/issuer",
            email = "issuer@example.com"
        )
        val generalNames = issuerAlternativeNameToGeneralNameArray(ian)
        val tags = generalNames.map { it.tagNo }.toSet()
        assertEquals(
            expected = setOf(GeneralName.uniformResourceIdentifier, GeneralName.rfc822Name),
            actual = tags,
        )

    }

    @Test
    fun issuerAlternativeNameToGeneralNameArrayIgnoresNulls() {
        val ian = IssuerAlternativeName(
            uri = null,
            email = null,
        )
        val generalNames = issuerAlternativeNameToGeneralNameArray(ian)
        assertEquals(
            expected = 0,
            actual = generalNames.size,
        )

    }

}