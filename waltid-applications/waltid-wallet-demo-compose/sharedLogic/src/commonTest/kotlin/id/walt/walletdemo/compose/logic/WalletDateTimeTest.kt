package id.walt.walletdemo.compose.logic

import kotlin.test.*
import kotlin.time.Instant

class WalletDateTimeTest {
    @Test
    fun offsetTimestampsIdentifyInstantsAndOtherValuesStayUninterpreted() {
        val expected = Instant.parse("2026-10-31T01:30:00.123456789Z")
        for (source in listOf("2026-10-30T23:30:00.123456789-02:00", "2026-10-31T03:30:00.123456789+02:00", "2026-10-31t01:30:00.123456789z", "2026-10-31T01:30:00.123456789-00:00")) {
            assertEquals(expected, WalletDateTime.parseTimestamp(source), source)
            val value = CredentialDisplayNormalizer.toDisclosureValue("\"$source\"", source, ClaimPath.topLevel("validUntil"), "ldp_vc")
            assertEquals(DisplayValue.InstantValue(expected), value)
        }
        for (source in listOf("2026-10-30", "2026-10-30T01:30:00", "2026-02-30T00:00:00Z", "2026-10-30T24:00:00Z", "2026-10-30T00:00:60Z", "2026-10-30T00:00:00+25:00", "2026-10-30T00:00:00+18:01", "0000-01-01T00:00:00Z", "2026-10-30T00:00:00+02", "2026-10-30T00:00:00+02:00:30", "2026-10-30T00:00:00Zextra", "2026-10-30T00:00:00.1234567891Z", "2026-10-30T00:00:00Z[Europe/Budapest]")) {
            assertNull(WalletDateTime.parseTimestamp(source), source)
        }
    }

    @Test
    fun w3cTimestampsWithoutOffsetUseUtcOnlyInTheirDefinedScope() {
        val source = "2026-10-30T23:30:00.125"
        val expected = DisplayValue.InstantValue(Instant.parse("2026-10-30T23:30:00.125Z"))
        for (format in listOf("jwt_vc", "jwt_vc_json", "jwt_vc_json-ld", "ldp_vc", "dc+sd-jwt", "vc+sd-jwt", "mso_mdoc")) {
            for (name in listOf("validFrom", "validUntil", "vc.validUntil", "proof.created", "vc.proof.expires", "credentialSubject.validUntil", "expiry_date")) {
                val value = CredentialDisplayNormalizer.toDisclosureValue(
                    "\"$source\"", source, ClaimPath.disclosure(0, name, format), format,
                )
                val isW3cTimestamp = format in listOf("jwt_vc", "jwt_vc_json", "jwt_vc_json-ld", "ldp_vc") &&
                    name in listOf("validFrom", "validUntil", "vc.validUntil", "proof.created", "vc.proof.expires")
                assertEquals(if (isW3cTimestamp) expected else DisplayValue.Text(source), value, "$format $name")
            }
        }
        for (source in listOf("2026-10-30", "2026-02-30T23:30:00", "2026-10-30T23:30:00+99:99")) {
            assertEquals(DisplayValue.Text(source), CredentialDisplayNormalizer.toDisclosureValue(
                "\"$source\"", source, ClaimPath.topLevel("validUntil"), "ldp_vc",
            ))
        }
    }

    @Test
    fun mdocDateAndTimestampClaimsRetainTheirDifferentMeaningsInDetailsAndConsent() {
        for (name in listOf("issue_date", "expiry_date", "portrait_capture_date")) {
            for (source in listOf("2026-10-30", "2026-10-30T02:00:00+02:00")) {
                val option = WalletDemoPresentationCredentialOption(
                    queryId = "mdl", credentialId = "one", format = "mso_mdoc",
                    issuer = null, subject = null, label = "mDL",
                    credentialDataJson = """{"org.iso.18013.5.1":{"$name":"$source"}}""",
                    disclosures = listOf(WalletDemoPresentationDisclosure(
                        path = "org.iso.18013.5.1/$name", label = name,
                        valueJson = "\"$source\"", displayValue = source, selectivelyDisclosable = false,
                    )),
                )
                val expected = if (source.length == 10) DisplayValue.Text(source)
                    else DisplayValue.InstantValue(Instant.parse("2026-10-30T00:00:00Z"))
                val items = option.toCredentialDetails().groups.flatMap { it.items }
                assertEquals(listOf(expected, expected), items.map { it.value }, "$name $source")
                assertTrue(items.all { it.rawValue == "\"$source\"" })
            }
        }
    }

    @Test
    fun formatUsesOffsetAtTheInstantIncludingDstAndDateBoundaries() {
        val cases = listOf(
            Triple("2026-03-29T00:30:00Z", "Europe/Budapest", "01:30:00 UTC+01:00"),
            Triple("2026-03-29T01:30:00Z", "Europe/Budapest", "03:30:00 UTC+02:00"),
            Triple("2026-10-25T00:30:00Z", "Europe/Budapest", "02:30:00 UTC+02:00"),
            Triple("2026-10-25T01:30:00Z", "Europe/Budapest", "02:30:00 UTC+01:00"),
            Triple("2026-10-30T00:00:00Z", "America/Los_Angeles", "17:00:00 UTC-07:00"),
            Triple("2026-10-30T00:00:00Z", "Asia/Kathmandu", "05:45:00 UTC+05:45"),
        )
        for ((raw, zone, time) in cases) {
            val formatted = WalletDateTime.format(Instant.parse(raw), zone, "en-GB")
            assertTrue(formatted.endsWith(time), "$raw in $zone: $formatted")
            if (zone == "America/Los_Angeles") assertTrue(formatted.contains("29 Oct 2026"), formatted)
        }
    }

    @Test
    fun localizationAndExactFractionsDoNotDependOnStoredFormatting() {
        val instant = Instant.parse("2026-10-30T13:30:00.123456789Z")
        val british = WalletDateTime.format(instant, "UTC", "en-GB")
        val american = WalletDateTime.format(instant, "UTC", "en-US")
        val german = WalletDateTime.format(instant, "UTC", "de-DE")
        assertTrue(british.contains("13:30:00.123456789"), british)
        assertTrue(american.contains("1:30:00.123456789") && american.contains("PM"), american)
        assertTrue(german.contains("13:30:00,123456789"), german)
        assertNotEquals(british, american)
    }
}
