package id.walt.credentials.utils

object HexUtils {

    /**
     * Checks if a string potentially represents valid hex encoding
     * and enforcing consistent casing for letters.
     *
     * Checks:
     * - Non-empty.
     * - Has an even number of characters.
     * - Contains only characters 0-9, a-f (if lowercase letters are used),
     *   OR 0-9, A-F (if uppercase letters are used). Mixed case letters are invalid.
     * - Strings containing only digits (0-9) are considered valid if they meet
     *   the length criteria.
     *
     * This version is optimized to perform checks in a single loop.
     *
     * @return `true` if the string matches the hex pattern, `false` otherwise.
     */
    fun String.matchesHex(): Boolean {
        // 1. Check for non-empty and even length (Essential first step)
        val len = this.length
        if (len == 0 || len % 2 != 0) {
            return false
        }

        // State: null = Undetermined (or only digits seen),
        //        false = Lowercase required, true = Uppercase required
        var requiresUppercase: Boolean? = null

        // 2. Single pass validation
        for (char in this) {
            when (char) {
                in '0'..'9' -> {
                    // Digits are always okay, continue loop
                    continue
                }
                in 'a'..'f' -> { // Lowercase letter
                    if (requiresUppercase == null) {
                        // First letter encountered, set state to require lowercase
                        requiresUppercase = false
                    } else if (requiresUppercase == true) {
                        // Expected uppercase, but found lowercase -> Mixed case
                        return false
                    }
                    // If requiresUppercase is false, it's consistent, continue
                }
                in 'A'..'F' -> { // Uppercase letter
                    if (requiresUppercase == null) {
                        // First letter encountered, set state to require uppercase
                        requiresUppercase = true
                    } else if (requiresUppercase == false) {
                        // Expected lowercase, but found uppercase -> Mixed case
                        return false
                    }
                    // If requiresUppercase is true, it's consistent, continue
                }
                else -> {
                    // Character is not a digit or a valid hex letter (a-f, A-F)
                    return false
                }
            }
        }

        // 3. If the loop completes without returning false, the string is valid
        return true
    }

// --- Examples ---


}
