package id.walt.webwallet.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.hours

/**
 * Service to periodically clean up expired tokens from the blacklist
 */
object TokenCleanupService {
    private val logger = KotlinLogging.logger {}
    private var cleanupJob: Job? = null
    
    /**
     * Start the token cleanup service
     */
    fun start() {
        if (cleanupJob?.isActive == true) {
            logger.warn { "Token cleanup service is already running" }
            return
        }
        
        cleanupJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    TokenBlacklistService.cleanupExpiredTokens()
                    val blacklistSize = TokenBlacklistService.getBlacklistSize()
                    logger.debug { "Token cleanup completed. Blacklist size: $blacklistSize" }
                } catch (e: Exception) {
                    logger.error(e) { "Error during token cleanup" }
                }
                
                // Run cleanup every hour
                delay(1.hours)
            }
        }
        
        logger.info { "Token cleanup service started" }
    }
    
    /**
     * Stop the token cleanup service
     */
    fun stop() {
        cleanupJob?.cancel()
        cleanupJob = null
        logger.info { "Token cleanup service stopped" }
    }
    
    /**
     * Check if the cleanup service is running
     */
    fun isRunning(): Boolean = cleanupJob?.isActive == true
}

