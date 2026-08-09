import XCTest
import WalletDemoIdentityDocumentSupport

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

    func testForeignRegistrationsAreNeverDeleted() {
        let plan = reconciliationPlan(
            desired: [],
            existing: [ExistingRegistration(documentIdentifier: "legacy-registration", documentType: mdl)],
            supportedDocumentTypes: supported
        )

        XCTAssertTrue(
            plan.isEmpty,
            "Only \(managedRegistrationIdentifierPrefix)-prefixed identifiers are owned by this integration"
        )
    }

    func testEmptyWalletClearsManagedRegistrations() {
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
