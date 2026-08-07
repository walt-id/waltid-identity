package id.walt.walletdemo.compose.logic

data class CredentialDetails(
    val summary: CredentialSummary,
    val groups: List<ClaimGroup>,
    /** Issuer display from stored sidecar metadata (`issuerDisplay`), when available. */
    val issuerDisplay: WalletDemoMetadataDisplay? = null,
)

data class ClaimGroup(
    val title: String,
    val items: List<ClaimItem>,
    val initiallyExpanded: Boolean = true,
)

class ClaimItemPath private constructor(
    private val renderedId: RenderedClaimPath,
) {
    val id: String = renderedId.value

    fun indexedChild(index: Int): ClaimItemPath =
        ClaimItemPath(
            renderedId = renderedId.indexed(index),
        )

    fun child(name: String): ClaimItemPath =
        ClaimItemPath(
            renderedId = renderedId.child(name),
        )

    companion object {
        fun root(): ClaimItemPath = ClaimItemPath(renderedId = RenderedClaimPath.raw(ClaimPathRoot.Root.id))

        fun topLevel(name: String): ClaimItemPath = ClaimItemPath(renderedId = RenderedClaimPath.raw(name))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClaimItemPath) return false

        return id == other.id
    }

    override fun hashCode(): Int =
        id.hashCode()

    override fun toString(): String =
        "ClaimItemPath(id=$id)"
}

private data class RenderedClaimPath(
    private val root: String,
    private val operations: List<PathOperation> = emptyList(),
) {
    val value: String
        get() = buildString {
            append(root)
            operations.forEach { operation ->
                when (operation) {
                    is PathOperation.Child -> append('.').append(operation.name)
                    is PathOperation.Index -> append('[').append(operation.value).append(']')
                }
            }
        }

    fun child(name: String): RenderedClaimPath =
        copy(operations = operations + PathOperation.Child(name))

    fun indexed(index: Int): RenderedClaimPath =
        copy(operations = operations + PathOperation.Index(index))

    companion object {
        fun raw(value: String): RenderedClaimPath = RenderedClaimPath(root = value)
    }
}

private sealed interface PathOperation {
    data class Child(val name: String) : PathOperation
    data class Index(val value: Int) : PathOperation
}

data class ClaimItem(
    val path: ClaimItemPath,
    val pathComponents: List<String> = emptyList(),
    val label: String,
    val value: DisplayValue,
    val rawValue: String? = null,
    val roles: Set<ClaimRole> = emptySet(),
)

sealed interface DisplayValue {
    data class Text(val value: String) : DisplayValue
    data class NumberValue(val value: String) : DisplayValue
    data class BooleanValue(val value: Boolean) : DisplayValue
    data class ObjectValue(val entries: List<ClaimItem>) : DisplayValue
    data class ListValue(val values: List<DisplayValue>) : DisplayValue
    data class Image(
        val encoded: String,
        val bytes: ByteArray,
        val mimeType: String,
        val byteCount: Int,
    ) : DisplayValue {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false

            return encoded == other.encoded &&
                    bytes.contentEquals(other.bytes) &&
                    mimeType == other.mimeType &&
                    byteCount == other.byteCount
        }

        override fun hashCode(): Int {
            var result = encoded.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + byteCount
            return result
        }
    }
    data class DecodedText(val value: String) : DisplayValue
    data class Raw(val value: String) : DisplayValue
    data object NullValue : DisplayValue
}
