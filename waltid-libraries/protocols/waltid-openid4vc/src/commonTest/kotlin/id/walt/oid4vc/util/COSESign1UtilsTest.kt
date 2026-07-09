package id.walt.oid4vc.util

import id.walt.oid4vc.OpenID4VC
import id.walt.oid4vc.providers.TokenTarget
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class COSESign1UtilsTest {
    @Test
    fun verifiesCwtProofSignedWithEmbeddedCoseKey() = runTest {
        assertTrue(OpenID4VC.verifyCOSESign1Signature(TokenTarget.PROOF_OF_POSSESSION, cwtProof))
    }

    @Test
    fun rejectsCwtProofWithTamperedSignature() = runTest {
        val tampered = cwtProof.dropLast(1) + if (cwtProof.last() == 'A') 'B' else 'A'

        assertFalse(OpenID4VC.verifyCOSESign1Signature(TokenTarget.PROOF_OF_POSSESSION, tampered))
    }

    private companion object {
        const val cwtProof =
            "hFhvowEmA3RvcGVuaWQ0dmNpLXByb29mK2N3dGhDT1NFX0tleVhLpAECIAEhWCCEnSEUG2nvaPVXbG6BAjnV6udZj-89fNeOcSDsDvT0VyJYIPjJtY-Wc9ZliVOzaspZNP-qio2eX5-nXc84hBbHED4UoRghgFg5pAFtd2FsbGV0LWNsaWVudAN2aHR0cHM6Ly9pc3N1ZXIuZXhhbXBsZQYaakJzJgpJbm9uY2UtMTIzWEDh-l42P_lKNDe4fHOLjjnld9rK65xCCiKetHWPmqVU7i1l7jTuGK8RCc_uSoeQ6vgCgYA1oKZMb6bG5xCOJnJy"
    }
}
