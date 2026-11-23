package id.walt.json

import org.erdtman.jcs.NumberToJSON

internal actual fun jsonNumberFormatter(): (Double) -> String = NumberToJSON::serializeNumber
