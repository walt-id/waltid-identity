import SwiftUI
import ImageIO

func paymentWebsite(_ value: String) -> URL? {
    guard !value.unicodeScalars.contains(where: { CharacterSet.whitespacesAndNewlines.contains($0) || CharacterSet.controlCharacters.contains($0) }),
          let url = URL(string: value), url.scheme?.lowercased() == "https",
          let host = url.host, !host.isEmpty, url.user == nil, url.password == nil else { return nil }
    return url
}

struct PaymentLogoView: View {
    let source: String
    let label: String
    let description: String?
    let allowRemote: Bool
    @State private var image: UIImage?
    @State private var loadedRequest: LoadRequest?

    var body: some View {
        Group {
            if let image, loadedRequest == LoadRequest(source: source, allowRemote: allowRemote) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(label).font(.caption)
                    Image(uiImage: image).resizable().scaledToFit().frame(width: 56, height: 56)
                        .accessibilityLabel(label).accessibilityIdentifier("payment-payee-logo")
                        .preference(key: CredentialImageReadinessKey.self, value: ["payment-payee-logo"])
                    if let description { Text(description).font(.caption) }
                }.frame(maxWidth: 80)
            }
        }.task(id: LoadRequest(source: source, allowRemote: allowRemote)) {
            image = nil
            let loaded = await loadPaymentLogo(source, allowRemote: allowRemote)
            if !Task.isCancelled {
                loadedRequest = LoadRequest(source: source, allowRemote: allowRemote)
                image = loaded
            }
        }
    }

    private struct LoadRequest: Equatable { let source: String; let allowRemote: Bool }
}

private let maxPaymentLogoBytes = 524_288

/// Mirrors the Compose content policy: bounded PNG/JPEG, explicit remote loading, no fallback.
func loadPaymentLogo(_ source: String, allowRemote: Bool = false) async -> UIImage? {
    let data: Data
    if source.hasPrefix("data:") {
        guard source.utf8.count <= maxPaymentLogoBytes * 4 / 3 + 64,
              let comma = source.firstIndex(of: ","),
              ["data:image/png;base64", "data:image/jpeg;base64"].contains(String(source[..<comma])),
              let decoded = Data(base64Encoded: String(source[source.index(after: comma)...])) else { return nil }
        data = decoded
    } else {
        guard allowRemote, let url = paymentWebsite(source), let host = url.host?.lowercased(),
              host != "localhost", !host.hasSuffix(".local"), !host.contains(":"),
              !host.allSatisfy({ $0.isNumber || $0 == "." }) else { return nil }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.httpShouldSetCookies = false
        configuration.urlCredentialStorage = nil
        configuration.timeoutIntervalForRequest = 5
        configuration.timeoutIntervalForResource = 5
        let session = URLSession(configuration: configuration, delegate: PaymentLogoRedirectPolicy(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        do {
            var request = URLRequest(url: url)
            request.setValue("image/png, image/jpeg", forHTTPHeaderField: "Accept")
            let (bytes, response) = try await session.bytes(for: request)
            guard let response = response as? HTTPURLResponse, (200..<300).contains(response.statusCode),
                  response.expectedContentLength <= maxPaymentLogoBytes else { return nil }
            var received = Data()
            for try await byte in bytes {
                received.append(byte)
                if received.count > maxPaymentLogoBytes { return nil }
            }
            data = received
        } catch { return nil }
    }
    guard !data.isEmpty, data.count <= maxPaymentLogoBytes,
          let imageSource = CGImageSourceCreateWithData(data as CFData, nil),
          let type = CGImageSourceGetType(imageSource) as String?, ["public.png", "public.jpeg"].contains(type),
          let properties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0, nil) as? [CFString: Any],
          let width = properties[kCGImagePropertyPixelWidth] as? Int,
          let height = properties[kCGImagePropertyPixelHeight] as? Int,
          (1...2048).contains(width), (1...2048).contains(height) else { return nil }
    return UIImage(data: data)
}

private final class PaymentLogoRedirectPolicy: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask,
                    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
                    completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}
