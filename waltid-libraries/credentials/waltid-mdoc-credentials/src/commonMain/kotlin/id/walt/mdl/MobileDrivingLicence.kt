package id.walt.mdl

import kotlinx.datetime.LocalDate

//TODO: Add handling for age_over_NN where multiple such thingies can be supported, not just static ones...
//TODO: Add handling for biometric_template_XX
//TODO: Add handling of other optional fields, e.g., sex, height, weight, hair and eye color etc.

data class MobileDrivingLicence(
    val familyName: String,
    val givenName: String,
    val birthDate: LocalDate,
    val issueDate: LocalDate,
    val expiryDate: LocalDate,
    val issuingCountry: String,
    val issuingAuthority: String,
    val documentNumber: String,
    val portrait: ByteArray,
    val drivingPrivileges: List<DrivingPrivilege>,
    val unitedNationsDistinguishingSign: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MobileDrivingLicence

        if (familyName != other.familyName) return false
        if (givenName != other.givenName) return false
        if (birthDate != other.birthDate) return false
        if (issueDate != other.issueDate) return false
        if (expiryDate != other.expiryDate) return false
        if (issuingCountry != other.issuingCountry) return false
        if (issuingAuthority != other.issuingAuthority) return false
        if (documentNumber != other.documentNumber) return false
        if (!portrait.contentEquals(other.portrait)) return false
        if (drivingPrivileges != other.drivingPrivileges) return false
        if (unitedNationsDistinguishingSign != other.unitedNationsDistinguishingSign) return false

        return true
    }

    override fun hashCode(): Int {
        var result = familyName.hashCode()
        result = 31 * result + givenName.hashCode()
        result = 31 * result + birthDate.hashCode()
        result = 31 * result + issueDate.hashCode()
        result = 31 * result + expiryDate.hashCode()
        result = 31 * result + issuingCountry.hashCode()
        result = 31 * result + issuingAuthority.hashCode()
        result = 31 * result + documentNumber.hashCode()
        result = 31 * result + portrait.contentHashCode()
        result = 31 * result + drivingPrivileges.hashCode()
        result = 31 * result + unitedNationsDistinguishingSign.hashCode()
        return result
    }

}
