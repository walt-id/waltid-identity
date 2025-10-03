package id.walt.webwallet.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Service to manage token blacklisting for proper logout functionality.
 * This ensures that tokens are invalidated on the server side even if they haven't expired.
 */
object TokenBlacklistService {
    private val logger = KotlinLogging.logger {}
    
    // In-memory storage for blacklisted tokens
    // In production, this should be replaced with Redis or a database
    private val blacklistedTokens = ConcurrentHashMap<String, Instant>()
    private val mutex = Mutex()
    
    /**
     * Add a token to the blacklist
     * @param token The JWT token to blacklist
     * @param expiresAt The expiration time of the token (for cleanup purposes)
     */
    suspend fun blacklistToken(token: String, expiresAt: Instant) {
        mutex.withLock {
            blacklistedTokens[token] = expiresAt
            logger.debug { "Token blacklisted: ${token.take(20)}..." }
        }
    }
    
    /**
     * Check if a token is blacklisted
     * @param token The JWT token to check
     * @return true if the token is blacklisted, false otherwise
     */
    suspend fun isTokenBlacklisted(token: String): Boolean {
        return mutex.withLock {
            val blacklistTime = blacklistedTokens[token]
            if (blacklistTime != null) {
                // If the token has expired, remove it from blacklist
                if (Instant.now().isAfter(blacklistTime)) {
                    blacklistedTokens.remove(token)
                    logger.debug { "Expired token removed from blacklist: ${token.take(20)}..." }
                    false
                } else {
                    true
                }
            } else {
                false
            }
        }
    }
    
    /**
     * Clean up expired tokens from the blacklist
     */
    suspend fun cleanupExpiredTokens() {
        mutex.withLock {
            val now = Instant.now()
            val expiredTokens = blacklistedTokens.entries.filter { it.value.isBefore(now) }
            expiredTokens.forEach { blacklistedTokens.remove(it.key) }
            if (expiredTokens.isNotEmpty()) {
                logger.debug { "Cleaned up ${expiredTokens.size} expired tokens from blacklist" }
            }
        }
    }
    
    /**
     * Get the number of blacklisted tokens (for monitoring)
     */
    suspend fun getBlacklistSize(): Int {
        return mutex.withLock {
            blacklistedTokens.size
        }
    }
}

