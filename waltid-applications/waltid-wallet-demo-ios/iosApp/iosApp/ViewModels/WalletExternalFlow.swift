import Foundation

enum WalletExternalFlow: Equatable {
    enum Kind: Equatable { case offer, presentation }
    case pending(URL, Kind)
    case active(URL, Kind)
    case unavailableCallback(URL)

    var url: URL { switch self { case let .pending(url, _), let .active(url, _), let .unavailableCallback(url): url } }
}
