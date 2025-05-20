package id.walt.cli.io

expect fun <R> Path.useLines(block: (Sequence<String>) -> R): R