package id.walt.verifier.openid.transactiondata

private const val MDOC_DEVICE_SIGNED_ITEM_PREFIX = "transaction_data_"

fun deviceSignedItemKey(index: Int): String = "$MDOC_DEVICE_SIGNED_ITEM_PREFIX$index"

fun deviceSignedItemKeys(transactionDataItemsCount: Int): Set<String> =
    (0..<transactionDataItemsCount).mapTo(mutableSetOf(), ::deviceSignedItemKey)

fun parseDeviceSignedItemIndex(key: String): Int? =
    key.removePrefix(MDOC_DEVICE_SIGNED_ITEM_PREFIX)
        .takeIf { it != key }
        ?.toIntOrNull()

fun requireContiguousIndices(indices: List<Int>) {
    indices.sorted().forEachIndexed { expectedIndex, actualIndex ->
        require(expectedIndex == actualIndex) {
            "mdoc transaction_data entries must use contiguous indices starting at 0"
        }
    }
}
