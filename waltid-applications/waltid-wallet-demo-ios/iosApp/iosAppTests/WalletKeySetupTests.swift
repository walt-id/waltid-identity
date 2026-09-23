import XCTest
@testable import iosApp

@MainActor
final class WalletKeySetupTests: XCTestCase {
    private typealias Model = WalletIdentityScreenModel
    private func option(_ recovery: String, _ storage: String, _ approval: String) -> Model.SetupOption {
        func value(_ id: String) -> Model.Selection { .init(id: id, title: id, detail: id) }
        return .init(recovery: value(recovery), storage: value(storage), approval: value(approval), restoring: false, perform: {})
    }

    func testRecoveryFiltersHardwareAndPreservesCompatibleApproval() {
        let options = [option("new", "hardware", "biometric"), option("backup", "native", "biometric"),
            option("backup", "native", "none"), option("backup", "database", "none")]
        let selected = Model.Step.recovery.select(options, selected: options[0], choiceID: "backup")
        XCTAssertEqual(selected.id, options[1].id)
        let storage = Model.Step.storage.options(options, selected: selected)
        XCTAssertEqual(Set(storage.map(\.storage.id)), ["native", "database"])
        let database = Model.Step.storage.select(options, selected: selected, choiceID: "database")
        XCTAssertEqual(database.id, options[3].id)
        XCTAssertEqual(Model.Step.approval.options(options, selected: database).map(\.approval.id), ["none"])
        XCTAssertEqual(Model.Step.approval.select(options, selected: database, choiceID: "biometric").id, database.id)
    }

    func testRestorationRecordsRemainSeparateAndBackPreservesSelection() {
        let options = [option("new", "native", "none"), option("restore:one", "native", "none"), option("restore:two", "native", "none")]
        XCTAssertEqual(Model.Step.recovery.options(options, selected: options[0]).count, 3)
        let restored = Model.Step.recovery.select(options, selected: options[0], choiceID: "restore:two")
        XCTAssertEqual(restored.id, options[2].id)
        XCTAssertEqual(Model.Step.recovery.select(options, selected: restored, choiceID: "new").id, options[0].id)
    }
}
