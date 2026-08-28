import SwiftUI
import UIKit
import WalletDemoQRCodeCore

public enum WalletQRCodeRenderer {
    public static func proximityImage(payload: String) -> UIImage? {
        guard payload.hasPrefix(proximityQrPrefix),
              payload.utf8.count <= maximumProximityPayloadBytes,
              payload.unicodeScalars.allSatisfy({ $0.value <= 0x7F }) else {
            return nil
        }
        guard let encoded = WalletDemoCreateProximityQRCode(payload) else {
            return nil
        }
        return UIImage(cgImage: encoded)
    }
}

/// Pixel-aligned QR image with a four-module quiet zone at every rendered size.
public struct WalletQRCodeView: View {
    @Environment(\.displayScale) private var displayScale
    private let image: UIImage

    public init(image: UIImage) {
        self.image = image
    }

    public var body: some View {
        GeometryReader { proxy in
            let availableSize = min(proxy.size.width, proxy.size.height)
            let moduleCount = image.cgImage?.width ?? Int(image.size.width)
            let layout = WalletQRCodeLayout(
                availableSize: availableSize,
                moduleCount: moduleCount,
                displayScale: displayScale
            )

            ZStack {
                Color.white
                Image(uiImage: image)
                    .interpolation(.none)
                    .resizable()
                    .frame(width: layout.matrixSize, height: layout.matrixSize)
            }
            .frame(width: layout.renderedSize, height: layout.renderedSize)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

struct WalletQRCodeLayout: Equatable {
    let moduleSize: CGFloat
    let matrixSize: CGFloat
    let quietZoneSize: CGFloat
    let renderedSize: CGFloat

    init(
        availableSize: CGFloat,
        moduleCount: Int,
        quietZoneModules: Int = 4,
        displayScale: CGFloat = 1
    ) {
        precondition(moduleCount > 0)
        precondition(quietZoneModules >= 0)
        precondition(displayScale > 0)

        let totalModules = moduleCount + quietZoneModules * 2
        let availablePixels = floor(availableSize * displayScale)
        let modulePixels = max(1, floor(availablePixels / CGFloat(totalModules)))
        let moduleSize = modulePixels / displayScale
        self.moduleSize = moduleSize
        matrixSize = CGFloat(moduleCount) * moduleSize
        quietZoneSize = CGFloat(quietZoneModules) * moduleSize
        renderedSize = CGFloat(totalModules) * moduleSize
    }
}

private let proximityQrPrefix = "mdoc:"
private let maximumProximityPayloadBytes = 2_953
