package id.walt.webwallet.utils

import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Utility class for parsing time duration strings with support for various formats
 */
object TimeUtils {
    
    /**
     * Parse a time duration string that can be in various formats:
     * - "15m" or "15 minutes" -> 15 minutes
     * - "2h" or "2 hours" -> 2 hours  
     * - "1d" or "1 days" -> 1 day
     * - "30s" or "30 seconds" -> 30 seconds
     * - "3600" -> 3600 seconds (fallback for numeric values)
     * 
     * @param timeString The time string to parse
     * @return Duration object
     */
    fun parseDuration(timeString: String): Duration {
        val trimmed = timeString.trim().lowercase()
        
        return when {
            trimmed.endsWith("s") || trimmed.endsWith("second") || trimmed.endsWith("seconds") -> {
                val value = trimmed.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                Duration.of(value, ChronoUnit.SECONDS)
            }
            trimmed.endsWith("m") || trimmed.endsWith("minute") || trimmed.endsWith("minutes") -> {
                val value = trimmed.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                Duration.of(value, ChronoUnit.MINUTES)
            }
            trimmed.endsWith("h") || trimmed.endsWith("hour") || trimmed.endsWith("hours") -> {
                val value = trimmed.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                Duration.of(value, ChronoUnit.HOURS)
            }
            trimmed.endsWith("d") || trimmed.endsWith("day") || trimmed.endsWith("days") -> {
                val value = trimmed.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                Duration.of(value, ChronoUnit.DAYS)
            }
            else -> {
                // Fallback: treat as seconds if it's a pure number
                val value = trimmed.toLongOrNull() ?: 0L
                Duration.of(value, ChronoUnit.SECONDS)
            }
        }
    }
    
    /**
     * Convert duration to seconds
     */
    fun toSeconds(duration: Duration): Long = duration.seconds
    
    /**
     * Convert duration to minutes
     */
    fun toMinutes(duration: Duration): Long = duration.toMinutes()
    
    /**
     * Convert duration to hours
     */
    fun toHours(duration: Duration): Long = duration.toHours()
    
    /**
     * Convert duration to days
     */
    fun toDays(duration: Duration): Long = duration.toDays()
}

