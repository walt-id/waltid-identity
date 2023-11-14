package id.walt.issuer.revocation.statuslist2021.storage

import id.walt.common.resolveContent
import id.walt.credentials.w3c.VerifiableCredential
import id.walt.services.WaltIdServices
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.io.path.Path
import kotlin.io.path.pathString

class WaltIdStatusListCredentialStorageService : StatusListCredentialStorageService() {

    override fun fetch(url: String): VerifiableCredential? = let {
        val path = getCredentialPath(url)
        resolveContent(path).takeIf { it != path }?.let {
            VerifiableCredential.fromJson(it)
        }
    }

    override fun store(credential: VerifiableCredential, url: String): Unit = File(getCredentialPath(url)).run {
        this.exists().takeIf { !it }?.let {
            this.createNewFile()
        }
        this.writeText(credential.encode())
    }

    private fun getCredentialPath(name: String) =
        Path(WaltIdServices.revocationDir, "${URLEncoder.encode(name, StandardCharsets.UTF_8)}.cred").pathString
}
