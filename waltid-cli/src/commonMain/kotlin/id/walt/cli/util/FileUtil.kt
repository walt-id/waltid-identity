package id.walt.cli.util

// TODO: Does it work on every environment? Or should we test if Windows?

fun getNormalizedPath(path: String) = path.replace("\\", "/")