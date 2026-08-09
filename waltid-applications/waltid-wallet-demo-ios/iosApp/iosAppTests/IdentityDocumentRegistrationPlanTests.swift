import XCTest
import WalletDemoIdentityDocumentSupport
import WalletSDK

/// Reconciliation semantics, exercised as a pure diff rather than against Apple's actor.
///
/// The Apple store wrapper around this planner does nothing but apply the plan, so these cases are
/// where registration correctness is actually decided.
final class IdentityDocumentRegistrationPlanTests: XCTestCase {
    private let mdl = "org.iso.18013.5.1.mDL"
    private let pid = "eu.europa.ec.eudi.pid.1"
    private var supported: Set<String> { [mdl, pid] }

    func testEmptyStoreAddsEveryDesiredRegistration() {
        let plan = reconciliationPlan(
            desired: [
                DesiredRegistration(documentIdentifier: "dc-a", documentType: mdl),
                DesiredRegistration(documentIdentifier: "dc-b", documentType: pid),
            ],
            existing: [],
            supportedDocumentTypes: supported
        )

        XCTAssertEqual(plan.toAdd.map(\.documentIdentifier), ["dc-a", "dc-b"])
        XCTAssertTrue(plan.toRemove.isEmpty)
    }

    func testExactMatchIsANoOp() {
        let plan = reconciliationPlan(
            desired: [DesiredRegistration(documentIdentifier: "dc-a", documentType: mdl)],
            existing: [ExistingRegistration(documentIdentifier: "dc-a", documentType: mdl)],
            supportedDocumentTypes: supported
        )

        XCTAssertTrue(plan.isEmpty, "Reconciling an unchanged wallet must not touch Apple's store")
    }

    func testDeletedCredentialRemovesOnlyItsRegistration() {
        let plan = reconciliationPlan(
            desired: [DesiredRegistration(documentIdentifier: "dc-a", documentType: mdl)],
            existing: [
                ExistingRegistration(documentIdentifier: "dc-a", documentType: mdl),
                ExistingRegistration(documentIdentifier: "dc-b", documentType: mdl),
            ],
            supportedDocumentTypes: supported
        )

        XCTAssertTrue(plan.toAdd.isEmpty)
        XCTAssertEqual(plan.toRemove, ["dc-b"])
    }

    func testDoctypeChangeUnderTheSameIdentifierIsAReplacement() {
        let plan = reconciliationPlan(
            desired: [DesiredRegistration(documentIdentifier: "dc-a", documentType: pid)],
            existing: [ExistingRegistration(documentIdentifier: "dc-a", documentType: mdl)],
            supportedDocumentTypes: supported
        )

        XCTAssertEqual(plan.toRemove, ["dc-a"], "Apple has no update operation, so the stale registration must go")
        XCTAssertEqual(plan.toAdd.map(\.documentType), [pid])
    }

    func testTwoCredentialsOfTheSameDoctypeStayTwoRegistrations() {
        let plan = reconciliationPlan(
            desired: [
                DesiredRegistration(documentIdentifier: "dc-a", documentType: mdl),
                DesiredRegistration(documentIdentifier: "dc-b", documentType: mdl),
            ],
            existing: [],
            supportedDocumentTypes: supported
        )

        XCTAssertEqual(plan.toAdd.count, 2, "Registrations are per credential, not per doctype")
    }

    func testUnsupportedDoctypeIsFilteredOutRatherThanRegistered() {
        let plan = reconciliationPlan(
            desired: [
                DesiredRegistration(documentIdentifier: "dc-a", documentType: "org.iso.23220.photoid.1"),
                DesiredRegistration(documentIdentifier: "dc-b", documentType: mdl),
            ],
            existing: [],
            supportedDocumentTypes: supported
        )

        XCTAssertEqual(
            plan.toAdd.map(\.documentIdentifier),
            ["dc-b"],
            "A doctype outside the entitlement would be rejected and abort the valid additions"
        )
    }

    func testUnsupportedDoctypeAlreadyRegisteredIsRemoved() {
        let plan = reconciliationPlan(
            desired: [DesiredRegistration(documentIdentifier: "dc-a", documentType: "org.iso.23220.photoid.1")],
            existing: [ExistingRegistration(documentIdentifier: "dc-a", documentType: "org.iso.23220.photoid.1")],
            supportedDocumentTypes: supported
        )

        XCTAssertEqual(plan.toRemove, ["dc-a"], "Narrowing the advertised doctypes must retire the registration")
        XCTAssertTrue(plan.toAdd.isEmpty)
    }

    func testARegistrationAbsentFromTheDesiredStateIsRemovedWhateverItsIdentifier() {
        // A published projection is authoritative for the whole store: this integration is the only
        // writer of Apple's registrations for this provider, so an identifier it does not currently
        // want registered is a stale one - from an older build's identifier scheme, or a credential
        // deleted while reconciliation could not run - and leaving it makes the platform offer a
        // document that cannot be presented.
        let plan = reconciliationPlan(
            desired: [],
            existing: [ExistingRegistration(documentIdentifier: "legacy-registration", documentType: mdl)],
            supportedDocumentTypes: supported
        )

        XCTAssertEqual(plan.toRemove, ["legacy-registration"])
        XCTAssertTrue(plan.toAdd.isEmpty)
    }

    func testEmptyWalletClearsRegistrations() {
        let plan = reconciliationPlan(
            desired: [],
            existing: [
                ExistingRegistration(documentIdentifier: "dc-a", documentType: mdl),
                ExistingRegistration(documentIdentifier: "dc-b", documentType: pid),
            ],
            supportedDocumentTypes: supported
        )

        XCTAssertEqual(plan.toRemove, ["dc-a", "dc-b"])
    }

    // MARK: - Fail-closed reads of the shared projection

    func testACorruptProjectionYieldsNoPlanAtAllRatherThanAnEmptyOne() {
        // An undecodable projection says nothing about what the wallet holds. Read as "nothing desired"
        // it would remove every `dc-` registration below, so it must not produce a desired set at all.
        XCTAssertThrowsError(try desiredRegistrations(from: .malformed(reason: "unexpected end of input"))) { error in
            XCTAssertEqual(
                error as? IdentityDocumentSupportFailure,
                .unreadableDesiredRegistrations("unexpected end of input"),
                "The decoder message is the only trace of this on a device, so it has to be carried"
            )
        }
    }

    func testAMissingProjectionYieldsNoPlanAtAllRatherThanAnEmptyOne() throws {
        XCTAssertNil(
            try desiredRegistrations(from: .missing),
            "A fresh install has published nothing, which is not the same as wanting nothing registered"
        )
    }

    func testAPublishedEmptyProjectionDoesUnregisterManagedDocuments() throws {
        // The counterpart to the two above: an empty projection *is* authoritative - the wallet's last
        // mdoc credential was deleted - so failing closed must not swallow this case too.
        let desired = try XCTUnwrap(
            try desiredRegistrations(from: .published(walletID: "test-123", registrations: []))
        )
        XCTAssertTrue(desired.isEmpty)

        let plan = reconciliationPlan(
            desired: desired,
            existing: [
                ExistingRegistration(documentIdentifier: "dc-a", documentType: mdl),
                ExistingRegistration(documentIdentifier: "dc-b", documentType: pid),
            ],
            supportedDocumentTypes: supported
        )

        XCTAssertEqual(plan.toRemove, ["dc-a", "dc-b"])
        XCTAssertTrue(plan.toAdd.isEmpty)
    }

    func testPlanIsDeterministicRegardlessOfInputOrder() {
        let forward = reconciliationPlan(
            desired: [
                DesiredRegistration(documentIdentifier: "dc-b", documentType: pid),
                DesiredRegistration(documentIdentifier: "dc-a", documentType: mdl),
            ],
            existing: [ExistingRegistration(documentIdentifier: "dc-c", documentType: mdl)],
            supportedDocumentTypes: supported
        )
        let reversed = reconciliationPlan(
            desired: [
                DesiredRegistration(documentIdentifier: "dc-a", documentType: mdl),
                DesiredRegistration(documentIdentifier: "dc-b", documentType: pid),
            ],
            existing: [ExistingRegistration(documentIdentifier: "dc-c", documentType: mdl)],
            supportedDocumentTypes: supported
        )

        XCTAssertEqual(forward, reversed)
    }
}
