package id.walt.commons.persistence

import kotlinx.serialization.Serializable

@Serializable
data class PersistenceNode(
    val host: String, val port: Int,
)

@Serializable
data class PersistenceConfiguration(
    val type: String = "memory", // memory, redis, redis-cluster
    val nodes: List<PersistenceNode>? = null,
    val user: String? = null,
    val password: String? = null,
)
