package id.walt.mdoc.objects

/** Parsed ISO mdoc `major.minor` version used to accept future compatible minor revisions deliberately. */
class MdocVersion private constructor(val major: UInt, val minor: UInt) : Comparable<MdocVersion> {
    override fun compareTo(other: MdocVersion): Int = compareValuesBy(this, other, MdocVersion::major, MdocVersion::minor)

    override fun toString(): String = "$major.$minor"

    companion object {
        fun parse(value: String): MdocVersion {
            val parts = value.split('.')
            require(parts.size == 2 && parts.all { part ->
                part.isNotEmpty() && part.all(Char::isDigit) && (part == "0" || !part.startsWith('0'))
            }) { "Mdoc version must use canonical major.minor notation" }
            return MdocVersion(
                parts[0].toUIntOrNull() ?: throw IllegalArgumentException("Mdoc major version is too large"),
                parts[1].toUIntOrNull() ?: throw IllegalArgumentException("Mdoc minor version is too large"),
            )
        }
    }
}
