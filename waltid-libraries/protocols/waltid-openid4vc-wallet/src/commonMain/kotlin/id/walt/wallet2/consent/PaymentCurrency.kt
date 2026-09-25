package id.walt.wallet2.consent

/** ISO 4217 List One, SIX Maintenance Agency, published 2026-09-17.
 * Source: https://www.six-group.com/dam/download/financial-information/data-center/iso-currrency/lists/list-one.xml
 * Source SHA-256: 33139b438657d1cee116ba737807ea71d19d6de4b90f799a09c56f0cc6a1b0ff
 * Codes without a defined minor unit are unsupported by this one-off payment renderer.
 */
internal fun paymentCurrencyMinorUnits(code: String): Int? = when (code) {
    "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF",
    "UGX", "UYI", "VND", "VUV", "XAF", "XOF", "XPF" -> 0
    "AED", "AFN", "ALL", "AMD", "AOA", "ARS", "AUD", "AWG", "AZN", "BAM",
    "BBD", "BDT", "BMD", "BND", "BOB", "BOV", "BRL", "BSD", "BTN", "BWP",
    "BYN", "BZD", "CAD", "CDF", "CHE", "CHF", "CHW", "CNY", "COP", "COU",
    "CRC", "CUP", "CVE", "CZK", "DKK", "DOP", "DZD", "EGP", "ERN", "ETB",
    "EUR", "FJD", "FKP", "GBP", "GEL", "GHS", "GIP", "GMD", "GTQ", "GYD",
    "HKD", "HNL", "HTG", "HUF", "IDR", "ILS", "INR", "IRR", "JMD", "KES",
    "KGS", "KHR", "KPW", "KYD", "KZT", "LAK", "LBP", "LKR", "LRD", "LSL",
    "MAD", "MDL", "MGA", "MKD", "MMK", "MNT", "MOP", "MRU", "MUR", "MVR",
    "MWK", "MXN", "MXV", "MYR", "MZN", "NAD", "NGN", "NIO", "NOK", "NPR",
    "NZD", "PAB", "PEN", "PGK", "PHP", "PKR", "PLN", "QAR", "RON", "RSD",
    "RUB", "SAR", "SBD", "SCR", "SDG", "SEK", "SGD", "SHP", "SLE", "SOS",
    "SRD", "SSP", "STN", "SVC", "SYP", "SZL", "THB", "TJS", "TMT", "TOP",
    "TRY", "TTD", "TWD", "TZS", "UAH", "USD", "USN", "UYU", "UZS", "VED",
    "VES", "WST", "XAD", "XCD", "XCG", "YER", "ZAR", "ZMW", "ZWG" -> 2
    "BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND" -> 3
    "CLF", "UYW" -> 4
    else -> null
}
