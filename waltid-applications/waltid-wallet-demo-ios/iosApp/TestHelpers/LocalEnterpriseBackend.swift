import Foundation

public struct LocalEnterpriseBackendConfig {
    public let hostAliasDomain: String
    public let apiBaseURL: URL
    public let ngrokBaseURL: URL
    public let adminEmail: String
    public let adminPassword: String
    public let organization: String
    public let tenant: String
    public let issuerProfile: String
    public let verifier: String

    public var tenantPath: String { "\(organization).\(tenant)" }

    public init(
        hostAliasDomain: String,
        apiBaseURL: URL,
        ngrokBaseURL: URL,
        adminEmail: String,
        adminPassword: String,
        organization: String,
        tenant: String,
        issuerProfile: String,
        verifier: String
    ) {
        self.hostAliasDomain = hostAliasDomain
        self.apiBaseURL = apiBaseURL
        self.ngrokBaseURL = ngrokBaseURL
        self.adminEmail = adminEmail
        self.adminPassword = adminPassword
        self.organization = organization
        self.tenant = tenant
        self.issuerProfile = issuerProfile
        self.verifier = verifier
    }

    public static func fromEnvironment(_ env: [String: String] = ProcessInfo.processInfo.environment) -> LocalEnterpriseBackendConfig {
        let hostAliasDomain = envValue("HOST_ALIAS_DOMAIN", from: env)
            ?? envValue("E2E_HOST_ALIAS_DOMAIN", from: env)
            ?? ""
        precondition(!hostAliasDomain.isEmpty, "HOST_ALIAS_DOMAIN (or E2E_HOST_ALIAS_DOMAIN) is required")

        let apiBase = envValue("E2E_API_BASE_URL", from: env) ?? "https://\(hostAliasDomain)"
        guard let apiBaseURL = URL(string: apiBase) else {
            fatalError("Invalid E2E_API_BASE_URL: \(apiBase)")
        }

        guard let ngrokBaseURL = URL(string: "https://\(hostAliasDomain)") else {
            fatalError("Invalid HOST_ALIAS_DOMAIN: \(hostAliasDomain)")
        }

        let attested = isAttested(env)
        let defaultIssuerProfile = attested ? "issuer2.mdl-profile" : "issuer2-noattest.mdl-profile"

        return LocalEnterpriseBackendConfig(
            hostAliasDomain: hostAliasDomain,
            apiBaseURL: apiBaseURL,
            ngrokBaseURL: ngrokBaseURL,
            adminEmail: envValue("E2E_ADMIN_EMAIL", from: env) ?? "admin@walt.id",
            adminPassword: envValue("E2E_ADMIN_PASSWORD", from: env) ?? "admin123456",
            organization: envValue("E2E_ORGANIZATION", from: env) ?? "waltid",
            tenant: envValue("E2E_TENANT", from: env) ?? "waltid-tenant01",
            issuerProfile: envValue("E2E_ISSUER_PROFILE", from: env) ?? defaultIssuerProfile,
            verifier: envValue("E2E_VERIFIER", from: env) ?? "verifier2"
        )
    }

    public static func isAttested(_ env: [String: String] = ProcessInfo.processInfo.environment) -> Bool {
        (envValue("E2E_ATTESTED", from: env) ?? "false").lowercased() == "true"
    }

    private static func envValue(_ name: String, from env: [String: String]) -> String? {
        env[name] ?? env["TEST_RUNNER_\(name)"]
    }
}

public struct LocalEnterpriseVerifierSession {
    public let sessionID: String
    public let bootstrapAuthorizationRequestURL: String

    public init(sessionID: String, bootstrapAuthorizationRequestURL: String) {
        self.sessionID = sessionID
        self.bootstrapAuthorizationRequestURL = bootstrapAuthorizationRequestURL
    }
}

public final class LocalEnterpriseBackend {
    private let client: WalletE2EClient

    public init(client: WalletE2EClient = WalletE2EClient()) {
        self.client = client
    }

    public func getAdminToken(config: LocalEnterpriseBackendConfig) async throws -> String {
        let body = try jsonString([
            "email": config.adminEmail,
            "password": config.adminPassword,
        ])
        let response = try await client.jsonRequest(
            url: config.apiBaseURL.appending(path: "/auth/account/emailpass"),
            method: "POST",
            headers: ["Content-Type": "application/json", "ngrok-skip-browser-warning": "true"],
            body: Data(body.utf8)
        )

        guard let token = response["token"] as? String, !token.isEmpty else {
            throw NSError(
                domain: "WalletE2E",
                code: 100,
                userInfo: [NSLocalizedDescriptionKey: "Missing token in auth response: \(response)"]
            )
        }
        return token
    }

    public func createPreAuthorizedOffer(config: LocalEnterpriseBackendConfig, token: String) async throws -> String {
        let endpoint = config.ngrokBaseURL.appending(path: "/v2/\(config.tenantPath).\(config.issuerProfile)/issuer-service-api/credentials/offers")
        let response = try await client.jsonRequest(
            url: endpoint,
            method: "POST",
            headers: [
                "Authorization": "Bearer \(token)",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning": "true",
            ],
            body: Data(try jsonString(["authMethod": "PRE_AUTHORIZED"]).utf8)
        )

        guard let offer = response["credentialOffer"] as? String, !offer.isEmpty else {
            throw NSError(
                domain: "WalletE2E",
                code: 101,
                userInfo: [NSLocalizedDescriptionKey: "Missing credentialOffer in response: \(response)"]
            )
        }
        return offer
    }

    public func createVerifierSession(config: LocalEnterpriseBackendConfig, token: String) async throws -> LocalEnterpriseVerifierSession {
        let payload: [String: Any] = [
            "flow_type": "cross_device",
            "core_flow": [
                "dcql_query": [
                    "credentials": [[
                        "id": "my_mdl",
                        "format": "mso_mdoc",
                        "meta": ["doctype_value": "org.iso.18013.5.1.mDL"],
                        "claims": [
                            ["path": ["org.iso.18013.5.1", "family_name"]],
                            ["path": ["org.iso.18013.5.1", "given_name"]],
                        ],
                    ]],
                ],
            ],
        ]

        let endpoint = config.ngrokBaseURL.appending(path: "/v1/\(config.tenantPath).\(config.verifier)/verifier2-service-api/verification-session/create")
        let response = try await client.jsonRequest(
            url: endpoint,
            method: "POST",
            headers: [
                "Authorization": "Bearer \(token)",
                "Content-Type": "application/json",
                "ngrok-skip-browser-warning": "true",
            ],
            body: Data(try jsonString(payload).utf8)
        )

        guard let sessionID = response["sessionId"] as? String, !sessionID.isEmpty,
              let requestURL = response["bootstrapAuthorizationRequestUrl"] as? String, !requestURL.isEmpty else {
            throw NSError(
                domain: "WalletE2E",
                code: 102,
                userInfo: [NSLocalizedDescriptionKey: "Invalid verifier session response: \(response)"]
            )
        }

        return LocalEnterpriseVerifierSession(
            sessionID: sessionID,
            bootstrapAuthorizationRequestURL: requestURL
        )
    }

    public func waitForVerifierStatus(
        config: LocalEnterpriseBackendConfig,
        sessionID: String,
        timeoutSeconds: TimeInterval
    ) async throws -> String {
        let deadline = Date().addingTimeInterval(timeoutSeconds)
        var lastStatus = "UNKNOWN"

        while Date() < deadline {
            let token = try await getAdminToken(config: config)
            let endpoint = config.apiBaseURL.appending(path: "/v1/\(config.tenantPath).\(config.verifier).\(sessionID)/verifier2-service-api/verification-session/info")
            let response = try await client.jsonRequest(
                url: endpoint,
                headers: [
                    "Authorization": "Bearer \(token)",
                    "ngrok-skip-browser-warning": "true",
                ]
            )

            if let session = response["session"] as? [String: Any], let status = session["status"] as? String {
                lastStatus = status
                if status == "SUCCESSFUL" || status == "FAILED" || status == "ERROR" || status == "EXPIRED" {
                    return status
                }
            }

            try await Task.sleep(nanoseconds: 2_000_000_000)
        }

        return lastStatus
    }
}
