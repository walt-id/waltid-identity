package id.walt.credentials.utils

object EnumUtils {
    /**
     * Gets the enum value by its name
     * @param [value] enum value
     * @return The enum value if found, otherwise - null
     */
    inline fun <reified T : Enum<T>> enumValueIgnoreCase(value: String): T? = enumValues<T>().firstOrNull {
        it.name.equals(value, true)
    }
}
