package id.walt.etsi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScrapedData(
    @SerialName("scraped_at")
    val scrapedAt: String,
    val formats: List<Format>
)

@Serializable
data class Format(
    val id: String,
    val name: String,
    val url: String,
    val profiles: List<Profile>
)

@Serializable
data class Profile(
    val id: String,
    val notes: String,
    @SerialName("test_cases")
    val testCases: List<TestCase>
)

@Serializable
data class TestCase(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val sections: List<TestCaseSection>
) {
    fun getSection(title: String): TestCaseSection? =
        sections.find { it.title.equals(title, ignoreCase = true) }

    fun getProtectedHeader(): TestCaseSection? = getSection("Protected Header")
    fun getPayload(): TestCaseSection? = getSection("Payload")
    fun getNamespace(): TestCaseSection? = getSection("NameSpace")
    fun getCwtClaims(): TestCaseSection? = sections.find { it.title.contains("CWT", ignoreCase = true) }
    fun getMsoPayload(): TestCaseSection? = sections.find { it.title.contains("Payload", ignoreCase = true) && it.title.contains("MSO", ignoreCase = true) || it.title.contains("MobileSecurityObject", ignoreCase = true) }

    val hasKeyBinding: Boolean
        get() = description.contains("cnf", ignoreCase = true) || 
                getPayload()?.items?.any { it.contains("cnf", ignoreCase = true) } == true

    val hasSelectiveDisclosure: Boolean
        get() = description.contains("selective disclosure", ignoreCase = true) ||
                description.contains("_sd", ignoreCase = true)

    val isPseudonym: Boolean
        get() = id.contains("pseudonym", ignoreCase = true) || description.contains("pseudonym", ignoreCase = true)

    val isOneTime: Boolean
        get() = id.contains("oneTime", ignoreCase = true) || description.contains("oneTime", ignoreCase = true)

    val isShortLived: Boolean
        get() = id.contains("shortLived", ignoreCase = true) || description.contains("shortLived", ignoreCase = true)
}

@Serializable
data class TestCaseSection(
    val title: String,
    val items: List<String>
)
