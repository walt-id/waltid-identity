import Foundation
import UIKit

enum PhysicalPrecondition: Error { case failed(String) }

/** Every physical test enters through this check; a simulator or absent selector is a failure. */
struct PhysicalPreflight {
    let configuration: String
    let runID: String
    let controller: URL
    let token: String
    static let peerRevision = "7c0988bee3384d13a0732e0c33336ae0faf3b863"

    init(environment: [String: String] = ProcessInfo.processInfo.environment) throws {
        #if targetEnvironment(simulator)
        throw PhysicalPrecondition.failed("Physical tests cannot execute on a simulator")
        #else
        guard ["CI", "GITHUB_ACTIONS", "GITLAB_CI", "BUILD_BUILDID", "JENKINS_URL", "TEAMCITY_VERSION"]
            .allSatisfy({ environment[$0] == nil }),
              environment["PROXIMITY_OPT_IN"] == "physical-local",
              let device = environment["PROXIMITY_DEVICE_ID"], !device.isEmpty,
              let reader = environment["PROXIMITY_READER_ID"], !reader.isEmpty, device != reader,
              environment["PROXIMITY_FIXTURE"] == "synthetic-ada-v1",
              environment["PROXIMITY_PEER_REVISION"] == Self.peerRevision,
              let bundle = environment["PROXIMITY_HOST_BUNDLE_ID"], bundle.hasSuffix(".proximityphysical"),
              Bundle.main.bundleIdentifier == bundle,
              let team = environment["PROXIMITY_TEAM_ID"], !team.isEmpty,
              let runID = environment["PROXIMITY_RUN_ID"], UUID(uuidString: runID) != nil,
              let configuration = environment["PROXIMITY_CONFIGURATION"],
              ["ble-gatt-central", "ble-gatt-peripheral", "ble-l2cap-central", "ble-l2cap-peripheral", "nfc-ble-continuation"].contains(configuration),
              let url = environment["PROXIMITY_CONTROL_URL"].flatMap(URL.init(string:)),
              url.scheme == "http", url.host != nil,
              let token = environment["PROXIMITY_CONTROL_TOKEN"], token.count == 64 else {
            throw PhysicalPrecondition.failed("Missing local opt-in, selected devices, disposable signed host, pinned peer or controller")
        }
        self.configuration = configuration
        self.runID = runID
        controller = url
        self.token = token
        #endif
    }

    func event(_ name: String, _ fields: [String: String] = [:]) async throws {
        var values = fields
        values["event"] = name
        values["role"] = "holder"
        values["elapsedNanos"] = String(DispatchTime.now().uptimeNanoseconds)
        var request = request(path: "events/\(name)")
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: values)
        let (_, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else {
            throw PhysicalPrecondition.failed("Local controller rejected phase evidence")
        }
    }

    func awaitApproval(round: Int) async throws {
        let deadline = Date().addingTimeInterval(30)
        while Date() < deadline {
            let (_, response) = try await URLSession.shared.data(for: request(path: "commands/approve-\(round)"))
            if (response as? HTTPURLResponse)?.statusCode == 200 { return }
            guard (response as? HTTPURLResponse)?.statusCode == 404 else {
                throw PhysicalPrecondition.failed("Local controller rejected approval polling")
            }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        throw PhysicalPrecondition.failed("Approval phase timed out")
    }

    private func request(path: String) -> URLRequest {
        var request = URLRequest(url: controller.appendingPathComponent(runID).appendingPathComponent(path), timeoutInterval: 3)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        return request
    }
}
