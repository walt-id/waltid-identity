import Foundation
import UIKit
import UserNotifications

/// App-level delivery. The system gets no wallet or credential identifiers.
@MainActor
public final class WalletDemoCredentialStatusNotifications: NSObject, UNUserNotificationCenterDelegate {
    public static let shared = WalletDemoCredentialStatusNotifications()
    public static let openHome = Notification.Name("wallet.credential-status.open-home")
    nonisolated private static let identifier = "wallet.credential-status"
    private var openRequested = false
    private var requestingPermission = false

    public func register() {
        UNUserNotificationCenter.current().delegate = self
    }

    public func takeHomeRequest() -> Bool {
        defer { openRequested = false }
        return openRequested
    }

    func requestPermissionIfNeeded() {
        guard !requestingPermission, UIApplication.shared.applicationState == .active else { return }
        requestingPermission = true
        Task {
            defer { requestingPermission = false }
            let center = UNUserNotificationCenter.current()
            guard await center.notificationSettings().authorizationStatus == .notDetermined,
                  UIApplication.shared.applicationState == .active else { return }
            _ = try? await center.requestAuthorization(options: [.alert])
        }
    }

    func postIfAllowed(isCurrent: @MainActor () -> Bool) async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        guard isCurrent(), UIApplication.shared.applicationState == .background,
              settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else { return }
        let content = UNMutableNotificationContent()
        content.body = String(localized: "A credential needs your attention.")
        try? await center.add(UNNotificationRequest(identifier: Self.identifier, content: content, trigger: nil))
    }

    public func clear() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [Self.identifier])
        center.removeDeliveredNotifications(withIdentifiers: [Self.identifier])
    }

    public nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        guard response.notification.request.identifier == Self.identifier,
              response.actionIdentifier == UNNotificationDefaultActionIdentifier else { return }
        await MainActor.run {
            self.openRequested = true
            NotificationCenter.default.post(name: Self.openHome, object: nil)
        }
    }

    public nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions { [] }
}
