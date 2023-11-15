package id.walt.issuer.revocation.statuslist2021.storage

import id.walt.issuer.utils.resolveContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.io.path.Path
import kotlin.io.path.pathString

class WaltIdStatusListCredentialStorageService : StatusListCredentialStorageService {
    private val revocationDir = "data/revocation"//TODO: config
    override fun fetch(url: String): JsonObject? = let {
        val path = getCredentialPath(url)
        resolveContent(path).takeIf { it != path }?.let {
            Json.decodeFromString(it)
        }
    }

    override fun store(credential: JsonObject, url: String): Unit = File(getCredentialPath(url)).run {
        this.exists().takeIf { !it }?.let {
            this.createNewFile()
        }
        this.writeText(credential.toString())
    }

    private fun getCredentialPath(name: String) =
        Path(revocationDir, "${URLEncoder.encode(name, StandardCharsets.UTF_8)}.cred").pathString
}
