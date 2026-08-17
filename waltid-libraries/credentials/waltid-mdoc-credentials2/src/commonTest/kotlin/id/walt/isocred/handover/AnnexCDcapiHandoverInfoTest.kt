@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.isocred.handover

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.handover.AnnexCDcapiHandoverInfo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToHexString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AnnexCDcapiHandoverInfoTest {

    private val encryptionInfo = "abc"
    private val origin = "https://verifier.example"

    /** `82` array(2) · `63 616263` "abc" · `7818 ...` "https://verifier.example" */
    private val dcapiInfoCborHex = "8263616263781868747470733a2f2f76657269666965722e6578616d706c65"

    /** SHA-256 of [dcapiInfoCborHex], computed independently of the code under test. */
    private val dcapiInfoHashHex = "b9c9b918824ee57c5af1d74030deebc9fe714161fc48eebc04fbc5258243ab6e"

    /** `83 f6 f6` [null, null, · `82 65 6463617069` ["dcapi", · `5820 ...` h'…'] */
    private val sessionTranscriptCborHex = "83f6f6826564636170695820$dcapiInfoHashHex"

    @Test
    fun dcapiInfoEncodesAsAnArrayOfEncryptionInfoAndOrigin() {
        assertEquals(
            dcapiInfoCborHex,
            coseCompliantCbor.encodeToHexString(AnnexCDcapiHandoverInfo(encryptionInfo, origin)),
        )
    }

    @Test
    fun sessionTranscriptIsNullNullDcapiInfoHash() {
        assertEquals(
            sessionTranscriptCborHex,
            coseCompliantCbor.encodeToHexString(
                SessionTranscript.serializer(),
                AnnexCDcapiHandoverInfo.sessionTranscript(encryptionInfo, origin),
            ),
        )
    }

    @Test
    fun hpkeInfoIsTheEncodedSessionTranscript() {
        assertContentEquals(
            coseCompliantCbor.encodeToByteArray(
                SessionTranscript.serializer(),
                AnnexCDcapiHandoverInfo.sessionTranscript(encryptionInfo, origin),
            ),
            AnnexCDcapiHandoverInfo.hpkeInfo(encryptionInfo, origin),
        )
    }

    /** A different origin must move the hash - the transcript is what binds the response to it. */
    @Test
    fun sessionTranscriptChangesWithOrigin() {
        assertEquals(
            false,
            coseCompliantCbor.encodeToHexString(
                SessionTranscript.serializer(),
                AnnexCDcapiHandoverInfo.sessionTranscript(encryptionInfo, origin),
            ) == coseCompliantCbor.encodeToHexString(
                SessionTranscript.serializer(),
                AnnexCDcapiHandoverInfo.sessionTranscript(encryptionInfo, "https://attacker.example"),
            ),
        )
    }
}
