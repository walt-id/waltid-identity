package id.walt.x509.iso

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class IsoCountryCodesJvmTest {

    @Test
    fun `common ISO country codes match JVM Locale list`() {
        // Locale.getISOCountries is the parity reference for the official ISO 3166-1 alpha-2 list on JVM.
        val localeCodes = Locale.getISOCountries().toSet()
        assertEquals(localeCodes, IsoCountryCodes.alpha2)
    }
}
