package id.walt.openid4vci

import kotlin.collections.LinkedHashSet

/**
 * Will Be Updated
 * Lightweight argument collection.
 *
 *
 *
 * We start with a LinkedHashSet to keep insertion order stable for joins while the skeleton only needs
 * membership checks and deduped appends. As we expand towards full parity we can grow this into a richer
 * collection without touching call sites.
 */
typealias Arguments = LinkedHashSet<String>

fun newArguments(): Arguments = LinkedHashSet()

fun argumentsOf(vararg values: String): Arguments = LinkedHashSet<String>().apply {
    values.forEach { value ->
        if (value.isNotEmpty()) {
            add(value)
        }
    }
}

fun Arguments.append(value: String) {
    add(value)
}

fun Arguments.has(value: String): Boolean = contains(value)