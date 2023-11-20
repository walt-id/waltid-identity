package id.walt.sdjwt

import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport.Ignore
@JsName("zzz_unused_SDMapBuilder")
class SDMapBuilder(
    private val decoyMode: DecoyMode = DecoyMode.NONE,
    private val numDecoys: Int = 0
) {
    private val fields = mutableMapOf<String, SDField>()

    fun addField(key: String, sd: Boolean, children: SDMap? = null): SDMapBuilder {
        fields.put(key, SDField(sd, children))
        return this
    }

    fun build(): SDMap {
        return SDMap(
            fields.toMap(),
            decoyMode = decoyMode, decoys = numDecoys
        )
    }
}
