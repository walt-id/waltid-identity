# Wallet date/time display policy

Apply these rules to every credential format in Compose Android/iOS/Web and native SwiftUI,
including the shared Apple provider UI. The same instant must have the same meaning in details,
receiving and consent, credential Added, reader-trust validity/updates and nearby-sharing
validity/receipts. Native punctuation may differ because platform locale data differs.

## Choose by meaning

| Value | Display rule |
| --- | --- |
| Instant: issued/not-before/expiry, added, completed, certificate validity | Current device timezone and locale. One continuous value containing the date, time and parenthesized geographic timezone, for example `30 Oct 2026, 01:00 (Budapest, CET)`. |
| Calendar date: birth date, a document's date-only issue/expiry date | Preserve the supplied calendar date. Never interpret midnight in UTC or shift it into another day. The wallet currently retains the source date text. |
| Local/floating date-time with no offset | Preserve the source value unless the governing specification explicitly supplies a default. Never silently attach the viewer's timezone. |
| Domain-specific zone: a venue, departure, jurisdiction or recurring schedule | Use the zone specified by that use case and label it. Preserve its IANA zone ID when the domain requires zone rules. An offset alone does not identify a region. |
| Unknown, malformed or unsupported value | Preserve the original representation; do not infer a date from magnitude, labels, identifiers or arbitrary timestamp-looking strings. |

A serialized offset such as `Z` or `+02:00` identifies an instant. It is not an instruction to
display credential expiry in the issuer's timezone. For example, `2026-10-30T23:30:00-02:00`
and `2026-10-31T01:30:00Z` display identically for the same viewer. In Budapest both are
31 October at 02:30 (Budapest, CET); in Los Angeles both are 30 October at 18:30 (Los Angeles, PDT).
Only explicit domain semantics override the device zone; no current wallet timestamp surface
requires such an override. Unrecognized zoned-date syntaxes remain source text.

## Formatting and ownership

- Keep parsed instants typed until rendering. A cached credential must not cache a timezone or
  localized date string. Refresh visible output after device settings changes or resuming the app.
- Use the user's locale and hour-cycle preference through platform formatters, with Gregorian
  dates matching credential calendar semantics. Show the year and time; never present an expiry
  instant as a date-only deadline. Omit seconds only when both seconds and fractions are zero;
  otherwise preserve seconds and exact nonzero fractional precision without rounding.
  Resolve the seasonal abbreviation for the timestamp's date, not the current date. Pair it with
  the geographic city, so abbreviations such as CST are not presented without context.
  Include the calendar era if a supported instant crosses into BCE in the viewer's zone.
- Keep date, time and timezone as separate localized fields from one settings snapshot. Render
  them with the same reusable component on every timestamp surface: one text value in the normal
  body style, with no forced line break. The timezone uses parentheses, never a middle-dot
  separator. Let the complete value wrap naturally in narrow layouts or at large text sizes;
  never truncate it or shrink the text to force one line. Accessibility reads the date, time and
  zone as one value. Isolate the parenthesized timezone with Unicode FSI/PDI so mixed scripts
  retain their punctuation and reading order. Calendar dates retain their source text without
  adding a time or timezone.
- Use platform CLDR names where available. Android and browser city names use CLDR's last-IANA-
  component fallback (underscores become spaces); Apple supplies localized exemplar cities.
  Short seasonal names may fall back to a localized GMT offset where the platform has no name,
  while retaining the city. UTC and fixed-offset zones have no invented geographic location.
  Never infer a city from an offset or maintain a custom city/abbreviation table.
- Keep source JSON/raw values unchanged for technical inspection and all protocol operations.
  Formatting is not validation, and localized strings must never feed expiry checks, storage,
  signing or presentation payloads. UTC remains appropriate for protocol serialization and logs.
- NumericDate JSON numbers are seconds since the Unix epoch, including negative/fractional
  values and exponent notation. Never guess milliseconds from magnitude. Retain exact precision
  up to nanoseconds and supported Gregorian years 0001–9999; unsupported values stay raw.
  Foundation `Date` values on SDK-backed native screens use millisecond display precision to
  avoid binary floating-point artifacts; parsed JSON claims retain exact nanoseconds.
  Numeric strings stay strings, so a year such as `"2026"` cannot become a date in 1970.
- Recognized timestamp strings normally require a complete offset-bearing date-time. Invalid dates,
  leap seconds, precision beyond nanoseconds and unsupported offset/zone syntax stay visible
  without lossy normalization. Date-only and floating values remain text. W3C credential
  `validFrom`/`validUntil` (at the root or inside the JWT `vc` envelope) recover a missing offset
  as UTC under VCDM 2.0 §5.8. Proof `created`/`expires` receive the same treatment under
  Data Integrity §2.1. These exceptions require a W3C credential format; it does not infer
  UTC for arbitrary subject fields, SD-JWT VC, mdoc, or transaction data.
- Shared Kotlin uses Kotlin time / kotlinx-datetime for parsing, instants and timezone semantics.
  Platform adapters supply locale-aware text; browser Intl supplies its host timezone rules.
  Native Swift follows the same contract using Foundation. No additional date library is needed.

Standard mdoc `issue_date`, `expiry_date` and `portrait_capture_date` follow their actual value:
full-date text remains a calendar date, while an offset-bearing tdate becomes a localized instant.
W3C proof `created` is recognized within the proof structure, not as a generic subject field.

## Standards and parser boundaries

| Governing specification | Relevant distinction |
| --- | --- |
| RFC 7519 §§2, 4.1.4–4.1.6; SD-JWT VC draft 23 §2.2.2 | `iat`, `nbf`, `exp` use numeric seconds. Fractional JSON numbers are allowed. A number-looking string is not a NumericDate. SD-JWT VC remains an Internet-Draft. |
| W3C VCDM 1.1 §§4.6, 4.8; VCDM 2.0 §§4.9, 5.8 | Issuance/expiration and validity timestamps carry calendar/time semantics. VCDM 2.0 uses XML Schema `dateTimeStamp` and explicitly defines UTC recovery for missing offsets. |
| RFC 3339 §§4.3, 5.6 | An offset identifies an instant. `-00:00` still identifies UTC, while withholding the source's local offset; it is not a floating time or a region ID. Raw text retains that distinction. |
| RFC 8949 §§3.4.1–3.4.2; RFC 8943 §1.1 | CBOR timestamps (tags 0/1) differ from calendar dates (tags 1004/100). The wallet's mdoc path retains `full-date` text; a birth/issue/expiry calendar date must not become an instant. |

This is a display interpreter, not a replacement credential validator or full ISO 8601/XML Schema
parser. Supported instant display uses years 0001–9999, nanoseconds, whole-minute source offsets
within ±18 hours and ordinary seconds 00–59. Valid but unsupported spellings (for example XML
Schema `24:00:00`, extended years or finer precision) remain visible as source text. A successful
display conversion does not certify that the source conforms to its credential profile.

Kotlin timestamps use `DateTimeComponents.Format` from the existing kotlinx-datetime dependency.
Library fields validate the calendar and offset; the format narrows its general ISO preset to
the source-offset syntax used here. This replaces the custom timestamp regex. `Instant.parse`
alone has broader acceptance/normalization behavior and is not an equivalent strict fallback.

Two bounded recognizers remain deliberately:

- NumericDate splits decimal/exponent text before exact integer arithmetic. A `Double` would lose
  nanoseconds (for example `1793318400.123456789`); common Kotlin has no standard `BigDecimal`.
  Adding an arbitrary-precision dependency just for this bounded conversion would increase the
  platform/dependency surface. Values beyond the supported precision/range remain unchanged.
- Native Swift separates exact fractional digits from a calendar-validated whole-second value.
  Foundation's `Date` parsers return binary floating-point seconds. Its component-based
  `DateComponents.ISO8601FormatStyle` requires iOS 26, above the app's iOS 15.4 minimum. Keep one
  compatible path rather than introducing OS-dependent acceptance or raising the deployment target.

## Evidence obligations

Cover equivalent NumericDate/offset timestamps, all supported credential formats, nested/array
values, day/year boundaries, negative and fractional seconds, DST gaps and overlaps, non-hour
offsets, locale/hour preferences, settings refresh, malformed values and unchanged raw data.
Keep rendered-state comparisons, app journeys and physical/system evidence distinct.

## Basis

- [RFC 7519 §2](https://www.rfc-editor.org/rfc/rfc7519.html#section-2): NumericDate seconds, including fractional values.
- [Unicode CLDR date/time-zone names](https://unicode.org/reports/tr35/tr35-dates.html#Time_Zone_Names): exemplar cities, specific seasonal names and documented fallbacks.
- [Unicode Bidirectional Algorithm](https://www.unicode.org/reports/tr9/#Explicit_Directional_Isolates): isolate a timezone whose script differs from the surrounding localized timestamp.
- [RFC 3339](https://www.rfc-editor.org/rfc/rfc3339.html): offset-bearing timestamps, distinct from unqualified local times.
- [SD-JWT VC draft 23](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-sd-jwt-vc-23#section-2.2.2): registered JWT claim semantics.
- [W3C VCDM 1.1](https://www.w3.org/TR/vc-data-model-1.1/#issuance-date) and [VCDM 2.0](https://www.w3.org/TR/vc-data-model-2.0/#representing-time): credential timestamps and missing-offset interpretation.
- [W3C Data Integrity §§2.1, 7.1](https://www.w3.org/TR/vc-data-integrity/): proof timestamps and guidance for human-facing time display.
- [RFC 8949](https://www.rfc-editor.org/rfc/rfc8949.html#section-3.4.1) and [RFC 8943](https://www.rfc-editor.org/rfc/rfc8943.html#section-1.1): CBOR time and date semantics.
- [W3C time/timezone guidance](https://www.w3.org/TR/timezone/): instants, floating values and domain-specific zones (Group Draft Note, guidance rather than a normative requirement).
- [Unicode CLDR date/time guidance](https://www.unicode.org/reports/tr35/tr35-dates.html): locale-dependent display and specific offsets versus generic zone names.
- [Apple QA1480](https://developer.apple.com/library/archive/qa/qa1480/_index.html): separate fixed protocol parsing from user-visible localized formatting.
- [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime): distinct physical instants and local civil time; localization belongs to platform adapters.
- [Kotlin ISO date-time components](https://kotlinlang.org/api/kotlinx-datetime/kotlinx-datetime/kotlinx.datetime.format/-date-time-components/-formats/-i-s-o_-d-a-t-e_-t-i-m-e_-o-f-f-s-e-t.html) and [Foundation ISO components](https://developer.apple.com/documentation/foundation/datecomponents/iso8601formatstyle): library parser alternatives.
