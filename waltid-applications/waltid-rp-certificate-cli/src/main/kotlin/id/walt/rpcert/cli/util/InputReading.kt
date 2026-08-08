package id.walt.rpcert.cli.util

import java.io.File

/** Resolves a CLI value that is either an inline string or, prefixed with `@`, a path to read it from. */
fun resolveInlineOrFile(value: String): String =
    if (value.startsWith("@")) File(value.removePrefix("@")).readText().trim()
    else value
