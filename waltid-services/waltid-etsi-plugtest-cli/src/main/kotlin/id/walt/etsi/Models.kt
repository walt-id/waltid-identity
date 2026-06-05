package id.walt.etsi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// ── Scraped file format (testcases/*.json) ────────────────────────────────────

@Serializable
data class ScrapedFile(
    @SerialName("scraped_at") val scrapedAt: String,
    val source: String,
    val sheets: List<ScrapedSheet>
)

@Serializable
data class ScrapedSheet(
    val sheetName: String,
    val profileText: String = "",
    val testcases: List<ScrapedTestCase>
)

@Serializable
data class ScrapedTestCase(
    val key: String = "",
    val name: String,
    val description: String = "",
    val protectedHeader: List<String> = emptyList(),
    val cwtClaims: List<String> = emptyList(),
    val payload: List<String> = emptyList(),
    val namespace: List<String> = emptyList(),
    val signature: List<String> = emptyList()
)

// ── Internal model (used by CLI) ──────────────────────────────────────────────

@Serializable
data class ScrapedData(
    @SerialName("scraped_at")
    val scrapedAt: String,
    val formats: List<Format>
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Load ScrapedData from a directory containing sd-jwt-vc.json, negative.json,
         * and iso-mdoc.json (as produced by scrape_testcases.sh).
         * Falls back gracefully if any file is missing.
         */
        fun loadFromDirectory(dir: File): ScrapedData {
            fun loadFile(filename: String): ScrapedFile? {
                val f = File(dir, filename)
                return if (f.exists()) json.decodeFromString<ScrapedFile>(f.readText()) else null
            }

            fun ScrapedTestCase.toTestCase(subtitle: String): TestCase {
                val sections = buildList {
                    if (protectedHeader.isNotEmpty()) add(TestCaseSection("Protected Header", protectedHeader))
                    if (cwtClaims.isNotEmpty())       add(TestCaseSection("CWT-claims (15)", cwtClaims))
                    if (payload.isNotEmpty())          add(TestCaseSection("Payload", payload))
                    if (namespace.isNotEmpty())        add(TestCaseSection("NameSpace", namespace))
                    if (signature.isNotEmpty())        add(TestCaseSection("Signature", signature))
                }
                val id = name.trim().split("  ")[0].trim()
                return TestCase(id = id, name = id, subtitle = subtitle, description = description, sections = sections)
            }

            fun ScrapedFile.toFormat(id: String, name: String) = Format(
                id = id, name = name, url = source,
                profiles = sheets.map { sheet ->
                    Profile(
                        id = sheet.sheetName.trim(),
                        notes = sheet.profileText,
                        testCases = sheet.testcases.map { it.toTestCase(sheet.sheetName.trim()) }
                    )
                }
            )

            val formats = buildList {
                loadFile("sd-jwt-vc.json")?.let  { add(it.toFormat("sd-jwt-vc",          "SD-JWT-VC Test Cases")) }
                loadFile("negative.json")?.let   { add(it.toFormat("sd-jwt-vc-negative", "SD-JWT-VC Negative Test Cases")) }
                loadFile("iso-mdoc.json")?.let   { add(it.toFormat("mdoc",               "ISO mdoc Test Cases")) }
            }

            val scrapedAt = formats.firstOrNull()?.let {
                // use the scraped_at from the first file — they're all scraped together
                loadFile("sd-jwt-vc.json")?.scrapedAt ?: ""
            } ?: ""

            return ScrapedData(scrapedAt = scrapedAt, formats = formats)
        }
    }
}

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
    fun getMsoPayload(): TestCaseSection? = sections.find {
        it.title.contains("Payload", ignoreCase = true) &&
            (it.title.contains("MSO", ignoreCase = true) || it.title.contains("MobileSecurityObject", ignoreCase = true))
    }

    val hasKeyBinding: Boolean
        get() = description.contains("cnf", ignoreCase = true) || 
                getPayload()?.items?.any { it.contains("cnf", ignoreCase = true) } == true

    val hasSelectiveDisclosure: Boolean
        get() = (description.contains("selective disclosure", ignoreCase = true) ||
                 description.contains("_sd", ignoreCase = true)) &&
                !description.contains("without selective disclosure", ignoreCase = true)

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
