package id.walt.itb

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Deployed case identifiers, independent of organisation/system/actor API credentials. */
@Serializable
data class ItbCatalogue(
    val observedOn: String,
    val testBed: String,
    val testBedVersion: String,
    val suites: List<Suite>,
) {
    internal val origin: Url get() = URLBuilder(testBed).apply { encodedPath = "" }.build()

    @Serializable
    data class Suite(
        val profile: String,
        val specification: String,
        val id: String,
        val name: String,
        val publishedVersion: String? = null,
        val cases: List<Case>,
    )

    @Serializable
    data class Case(val id: String, val name: String)

    init {
        require(suites.isNotEmpty()) { "The ITB catalogue must contain suites" }
        require(suites.map { it.id }.distinct().size == suites.size) { "Duplicate ITB suite IDs" }
        suites.forEach { suite ->
            require(suite.id.isNotBlank() && suite.cases.isNotEmpty()) { "Empty ITB suite" }
            require(suite.cases.all { it.id.isNotBlank() && it.name.isNotBlank() }) { "Empty ITB case identity" }
            require(suite.cases.map { it.id }.distinct().size == suite.cases.size) {
                "Duplicate ITB case IDs in ${suite.id}"
            }
        }
    }

    companion object {
        fun initialWalletCases(): ItbCatalogue = requireNotNull(
            ItbCatalogue::class.java.getResourceAsStream("/itb/wal-1423-catalogue.json")
        ) { "The deployed ITB case catalogue is missing" }.bufferedReader().use {
            Json.decodeFromString<ItbCatalogue>(it.readText())
        }
    }
}
