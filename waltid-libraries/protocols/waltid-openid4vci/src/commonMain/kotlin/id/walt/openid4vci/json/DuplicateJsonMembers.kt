package id.walt.openid4vci.json

import id.walt.json.jsonHasDuplicateMembers as sharedJsonHasDuplicateMembers

/**
 * Returns true when [raw] is a JSON text whose objects contain a repeated member name.
 * Rejects containers deeper than 128 levels before the JSON tree parser runs.
 * Malformed strings and excessive nesting throw [IllegalArgumentException].
 */
internal fun jsonHasDuplicateMembers(raw: String): Boolean =
    sharedJsonHasDuplicateMembers(raw)
