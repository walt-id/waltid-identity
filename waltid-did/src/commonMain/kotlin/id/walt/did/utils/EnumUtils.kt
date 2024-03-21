package id.walt.did.utils

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object EnumUtils {
    /**
     * Gets the enum value by its name
     * @param [value] enum value
     * @return The enum value if found, otherwise - null
     */
    @JsExport.Ignore
    inline fun <reified T : Enum<T>> enumValueIgnoreCase(value: String): T? = enumValues<T>().firstOrNull {
        it.name.equals(value, true)
    }
}
