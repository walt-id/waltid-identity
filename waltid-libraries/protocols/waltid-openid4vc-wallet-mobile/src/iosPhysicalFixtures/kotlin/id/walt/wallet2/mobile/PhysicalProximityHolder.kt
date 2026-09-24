package id.walt.wallet2.mobile

import id.walt.mdoc.proximity.mobile.IosBleProximityTransportFactory
import id.walt.mdoc.proximity.mobile.NfcHostPlatformAdapter
import id.walt.proximity.physical.PhysicalCredentialFixture

/** Disposable native test host; compiled only into the isolated physical-fixtures framework. */
public class PhysicalProximityHolder private constructor(
    private val fixture: PhysicalCredentialFixture,
    private val coordinator: ProximityCoordinator,
) {
    public val rootHex: String get() = fixture.root.toHexString()
    public val wrongRootHex: String get() = fixture.untrustedRoot.toHexString()

    public suspend fun start(configuration: ProximityConfiguration): ProximitySession = coordinator.start(configuration)
    public suspend fun close() { fixture.runtime.close() }

    public companion object {
        public suspend fun create(nfcHost: NfcHostPlatformAdapter): PhysicalProximityHolder {
            val fixture = PhysicalCredentialFixture.create()
            return PhysicalProximityHolder(fixture,
                ProximityCoordinator(fixture.wallet, IosBleProximityTransportFactory(), nfcHost))
        }
    }
}
