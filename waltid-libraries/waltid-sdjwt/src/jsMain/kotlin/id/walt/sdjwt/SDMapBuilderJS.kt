package id.walt.sdjwt

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("SDMapBuilder")
class SDMapBuilderJS(
    private val decoyMode: String = DecoyMode.NONE.name,
    private val numDecoys: Int = 0
) {
    private val fields = mutableMapOf<String, SDField>()

    fun addField(key: String, sd: Boolean, children: dynamic = null): SDMapBuilderJS {
        val childrenSdMap = if (children != null) {
            SDMap.fromJSON(JSON.stringify(children))
        } else null
        fields[key] = SDField(sd, childrenSdMap)
        return this
    }

    fun buildAsJSON(): dynamic {
        return JSON.parse(
            SDMap(
                fields, DecoyMode.valueOf(decoyMode), numDecoys
            ).toJSON().toString()
        )
    }

    fun build(): SDMap {
        return SDMap(fields, DecoyMode.valueOf(decoyMode), numDecoys)
    }

    fun buildFromJsonPaths(jsonPaths: Array<String>): dynamic {
        return JSON.parse(SDMap.generateSDMap(jsonPaths.toList(), decoyMode = DecoyMode.valueOf(decoyMode), numDecoys).toJSON().toString())
    }
}
