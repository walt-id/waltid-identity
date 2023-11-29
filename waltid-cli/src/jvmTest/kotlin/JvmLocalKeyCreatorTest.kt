import TestUtils.loadJwkLocal
import TestUtils.loadPemLocal
import TestUtils.loadResourceBytes
import TestUtils.loadSerializedLocal
import id.walt.crypto.keys.JvmLocalKeyCreator
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKeyMetadata
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal typealias importRawAssertions = (expected: JsonObject, actual: JsonObject) -> Unit
