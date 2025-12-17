package id.walt.issuer.services.onboarding.models

import java.util.*

internal object ValidationUtils {

    fun isValidISOCountryCode(code: String): Boolean {
        val normalized = code.uppercase(Locale.US)
        return normalized.length == 2 && Locale.getISOCountries().contains(normalized)
    }
}
