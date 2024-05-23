package id.walt.webwallet.db

import com.zaxxer.hikari.HikariConfig
import id.walt.webwallet.config.WalletConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SerializableHikariConfiguration private constructor(
//TODO: remove primary constructor leak via the copy method
    val jdbcUrl: String? = null,
    val driverClassName: String? = null,
    val username: String? = null,
    val password: String? = null,
    val transactionIsolation: String? = null,
    val maximumPoolSize: Int? = null,
    val maxLifetime: Long? = null,
    val isAutoCommit: Boolean? = null,
    val dataSourceProperties: JsonObject? = null,
) {
    constructor(
        jdbcUrl: String? = null,
        driverClassName: String? = null,
        username: String? = null,
        password: String? = null,
        transactionIsolation: String? = null,
        maximumPoolSize: Int? = null,
        maxLifetime: Long? = null,
        isAutoCommit: Boolean? = null,
        dataSourceProperties: JsonObject? = null,
        dummy: String? = null,//so constructor signature is different
    ) : this(
        jdbcUrl?.let { WalletConfig.fixEnvVars(it) },
        driverClassName,
        username?.let { WalletConfig.fixEnvVar(it) },
        password?.let { WalletConfig.fixEnvVar(it) },
        transactionIsolation,
        maximumPoolSize,
        maxLifetime,
        isAutoCommit,
        dataSourceProperties,
    )

    fun toHikariConfig() = run obj@{
        HikariConfig().apply {
            this.jdbcUrl = this@obj.jdbcUrl
            this.driverClassName = this@obj.driverClassName
            this.username = this@obj.username
            this.password = this@obj.password

            this@obj.transactionIsolation?.let { this.transactionIsolation = it }
            this@obj.maximumPoolSize?.let { this.maximumPoolSize = it }
            this@obj.maxLifetime?.let { this.maxLifetime = it }
            this@obj.isAutoCommit?.let { this.isAutoCommit = it }

            this@obj.dataSourceProperties?.entries?.forEach { (key, value) ->
                this.addDataSourceProperty(key, value.jsonPrimitive.content)
            }
        }
    }
}