package id.walt.webwallet.config

import com.zaxxer.hikari.HikariDataSource
import id.walt.webwallet.db.Db
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists

@Serializable
data class DatabaseConfiguration(
    val database: String
) : WalletConfig()

//@Serializable
data class DatasourceJsonConfiguration(
    val hikariDataSource: JsonObject,
    val recreateDatabaseOnStart: Boolean = false
) : WalletConfig() {

    companion object {
        private val log = KotlinLogging.logger {  }
    }

    val jdbcUrl by lazy { hikariDataSource.jsonObject["jdbcUrl"]?.jsonPrimitive?.content }

    init {
        if (jdbcUrl?.startsWith(Db.SQLITE_PREFIX) == true) {
            val path = Path(jdbcUrl!!.removePrefix(Db.SQLITE_PREFIX))
            if (path.notExists()) {
                log.info { "Creating directory for sqlite database: $path" }
                path.createParentDirectories()
            }
        }
    }
}
