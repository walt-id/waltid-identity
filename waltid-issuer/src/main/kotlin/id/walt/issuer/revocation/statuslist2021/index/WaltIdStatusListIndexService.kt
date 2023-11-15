package id.walt.issuer.revocation.statuslist2021.index

import id.walt.issuer.utils.resolveContent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class WaltIdStatusListIndexService(
    private val indexingStrategy: IndexingStrategy,
) : StatusListIndexService {
    private val indexRootPath = "data/revocation"//TODO: config

    override fun index(url: String): String = let {
        checkIndex(url).takeIf { it } ?: createIndex(url)
    }.let {
        resolveContent(getIndexPath(url))
    }.let{
        val bitset: Array<String> = Json.decodeFromString(it)
        val index = generate(bitset)
        updateIndex(url, bitset.plus(index))
        index
    }

    private fun generate(occupied: Array<String>): String = indexingStrategy.next(occupied)

    private fun getIndexPath(url: String) = "$indexRootPath/${URLEncoder.encode(url, StandardCharsets.UTF_8)}.index"

    private fun createIndex(url: String) = File(getIndexPath(url)).run {
        this.createNewFile()
        this.writeText(Json.encodeToString(emptyArray<String>()))
    }

    private fun checkIndex(url: String) = File(getIndexPath(url)).exists()

    private fun updateIndex(url: String, bitset: Array<String>){
        File(getIndexPath(url)).writeText(Json.encodeToString(bitset))
    }
}
