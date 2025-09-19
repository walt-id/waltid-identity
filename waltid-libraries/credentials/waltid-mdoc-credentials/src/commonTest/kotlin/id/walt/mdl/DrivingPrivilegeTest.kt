@file:OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)

package id.walt.mdl

import cbor.Cbor
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

class DrivingPrivilegeTest {

    private val payloads = listOf(
        DrivingPrivilege(
            vehicleCategoryCode = "some-cat",
        ),
        DrivingPrivilege(
            vehicleCategoryCode = "some-cat-1",
            issueDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        ),
        DrivingPrivilege(
            vehicleCategoryCode = "some-cat-1",
            expiryDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        ),
        DrivingPrivilege(
            vehicleCategoryCode = "some-cat-1",
            expiryDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            issueDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            codes = listOf(),
        ),
        DrivingPrivilege(
            vehicleCategoryCode = "some-cat-1",
            expiryDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            issueDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            codes = listOf(
                CodeInfo(
                    code = "some-code",
                )
            ),
        ),
        DrivingPrivilege(
            vehicleCategoryCode = "some-cat-1",
            expiryDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            issueDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            codes = listOf(
                CodeInfo(
                    code = "some-code",
                ),
                CodeInfo(
                    code = "some-code",
                    sign = "some-sign",
                ),
                CodeInfo(
                    code = "some-code",
                    value = "some-value",
                ),
                CodeInfo(
                    code = "some-code",
                    sign = "some-sign",
                    value = "some-value",
                ),
            ),
        ),
    )

    @Test
    fun testJsonSerialize() {

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = DrivingPrivilege.fromJSON(payload.toJSON()),
            )

            assertEquals(
                expected = payload,
                actual = Json.decodeFromJsonElement(payload.toJSON()),
            )

            assertEquals(
                expected = payload,
                actual = Json.decodeFromJsonElement(Json.encodeToJsonElement(payload)),
            )

            assertEquals(
                expected = payload,
                actual = Json.decodeFromString<DrivingPrivilege>(Json.encodeToString(payload)),
            )
        }
    }

    @Test
    fun testCborSerialize() {

        payloads.forEach { payload ->

            assertEquals(
                expected = payload,
                actual = DrivingPrivilege.fromCBOR(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = DrivingPrivilege.fromCBORHex(payload.toCBORHex()),
            )

            assertEquals(
                expected = payload,
                actual = DrivingPrivilege.fromMapElement(payload.toMapElement()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromByteArray<DrivingPrivilege>(payload.toCBOR()),
            )

            assertEquals(
                expected = payload,
                actual = Cbor.decodeFromHexString<DrivingPrivilege>(payload.toCBORHex()),
            )
        }

    }
}
