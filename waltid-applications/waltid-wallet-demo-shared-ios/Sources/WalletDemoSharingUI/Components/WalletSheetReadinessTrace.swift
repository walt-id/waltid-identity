import Foundation
import UIKit

// Temporary diagnostic only. No content, accessibility text, URLs or credential values.
enum WalletSheetReadinessTrace {
    private static let queue = DispatchQueue(label: "wallet.sheet-readiness-trace")
    private static let lock = NSLock()
    private static var reserved = 0
    private static var writtenBytes = 0
    private static var outputClosed = false
    private static let maximumRecords = 512
    private static let maximumBytes = 4 * 1024 * 1024
    private static let output: FileHandle? = {
        let name = "sheet-readiness-\(ProcessInfo.processInfo.processIdentifier)-\(UUID().uuidString).jsonl"
        let path = FileManager.default.temporaryDirectory.appendingPathComponent(name).path
        FileManager.default.createFile(atPath: path, contents: nil)
        return FileHandle(forWritingAtPath: path)
    }()

    static func identity(_ object: AnyObject?) -> String {
        guard let object else { return "nil" }
        return String(describing: Unmanaged.passUnretained(object).toOpaque())
    }

    static func root(of page: UIViewController?) -> UIViewController? {
        var owner = page
        while let parent = owner?.parent { owner = parent }
        return owner
    }

    static func transition(_ value: UIViewControllerTransitionCoordinator?) -> [String: Any] {
        guard let value else { return ["present": false] }
        return ["present": true, "id": identity(value as AnyObject),
                "class": String(describing: type(of: value)), "animated": value.isAnimated,
                "interactive": value.isInteractive, "cancelled": value.isCancelled,
                "duration": String(value.transitionDuration), "percent": String(Double(value.percentComplete)),
                "presentationStyle": value.presentationStyle.rawValue]
    }

    static func controllers(page: UIViewController?, owner suppliedOwner: UIViewController? = nil) -> [String: Any] {
        let owner = suppliedOwner ?? root(of: page)
        return ["page": identity(page), "pageParent": identity(page?.parent),
                "pageClass": page.map { String(describing: type(of: $0)) } ?? "nil",
                "pageWindow": identity(page?.viewIfLoaded?.window),
                "owner": identity(owner), "ownerClass": owner.map { String(describing: type(of: $0)) } ?? "nil",
                "ownerWindow": identity(owner?.viewIfLoaded?.window),
                "ownerHasPresenter": owner?.presentingViewController != nil,
                "ownerBeingPresented": owner?.isBeingPresented ?? false,
                "ownerBeingDismissed": owner?.isBeingDismissed ?? false,
                "pageTransition": transition(page?.transitionCoordinator),
                "navigationTransition": transition(page?.navigationController?.transitionCoordinator),
                "ownerTransition": transition(owner?.transitionCoordinator)]
    }

    static func rect(_ value: CGRect) -> [String] {
        [value.origin.x, value.origin.y, value.width, value.height].map { String(Double($0)) }
    }

    private static func geometry(_ layer: CALayer) -> [String: Any] {
        let t = layer.transform
        return ["bounds": rect(layer.bounds),
                "position": [String(Double(layer.position.x)), String(Double(layer.position.y))],
                "transform": [t.m11, t.m12, t.m13, t.m14, t.m21, t.m22, t.m23, t.m24,
                              t.m31, t.m32, t.m33, t.m34, t.m41, t.m42, t.m43, t.m44].map { String(Double($0)) }]
    }

    static func layers(from root: CALayer?, through container: CALayer?,
        blocked: CALayer, renderedAtPredicate: CALayer?) -> [String: Any] {
        var rows: [[String: Any]] = []
        var layer = root
        while let current = layer, rows.count < 24 {
            // Reuse the presentation object from the actual failing predicate for its layer.
            let rendered = current === blocked ? renderedAtPredicate : current.presentation()
            rows.append(["id": identity(current), "class": String(describing: type(of: current)),
                         "delegateClass": current.delegate.map { String(describing: type(of: $0)) } ?? "nil",
                         "blockingLayer": current === blocked, "isContainer": current === container,
                         "animationKeys": (current.animationKeys() ?? []).prefix(16).map { String($0.prefix(128)) },
                         "model": geometry(current), "presentationNil": rendered == nil,
                         "presentation": rendered.map { geometry($0) } ?? [:]])
            if current === container { layer = nil; break }
            layer = current.superlayer
        }
        return ["ancestors": rows, "ancestorsTruncated": layer != nil, "blockingLayer": identity(blocked)]
    }

    static func emit(_ event: String, _ fields: @autoclosure () -> [String: Any]) {
        lock.lock()
        guard reserved < maximumRecords else { lock.unlock(); return }
        reserved += 1
        let sequence = reserved
        lock.unlock()
        // Snapshot only value metadata here. The writer never captures UIKit owners/layers.
        var row: [String: Any] = sequence == maximumRecords ? ["limit": "records"] : fields()
        row["event"] = sequence == maximumRecords ? "trace_limit" : event
        row["schema"] = 1; row["sequence"] = sequence
        row["pid"] = ProcessInfo.processInfo.processIdentifier
        row["time"] = String(CACurrentMediaTime()); row["unixTime"] = String(Date().timeIntervalSince1970)
        row["mainThread"] = Thread.isMainThread
        let record = row
        queue.async {
            guard !outputClosed else { return }
            guard var data = try? JSONSerialization.data(withJSONObject: record, options: [.sortedKeys]) else { return }
            if data.count > 64 * 1024 {
                data = Data("{\"event\":\"record_too_large\",\"sequence\":\(sequence)}".utf8)
            }
            if writtenBytes + data.count + 1 > maximumBytes - 128 {
                data = Data("{\"event\":\"trace_limit\",\"limit\":\"bytes\"}".utf8)
                outputClosed = true
            }
            data.append(10)
            if let output {
                do { try output.write(contentsOf: data) }
                catch { outputClosed = true; NSLog("SHEET_READINESS_TRACE write-failed") }
            } else { outputClosed = true; NSLog("SHEET_READINESS_TRACE output-unavailable") }
            writtenBytes += data.count
        }
    }
}

// Per-owner metadata only: first observation, then at 1/5/30 seconds on repeated observations.
// No timer or additional callback is installed; sampling occurs only on existing calls.
final class WalletSheetReadinessSamples {
    private var samples: [String: (first: CFTimeInterval, next: Int)] = [:]
    private let offsets: [CFTimeInterval] = [0, 1, 5, 30]

    func reset() { samples.removeAll(keepingCapacity: true) }

    func emit(_ event: String, key: String, _ fields: @autoclosure () -> [String: Any]) {
        let now = CACurrentMediaTime()
        if samples[key] == nil && samples.count >= 64 { return }
        var sample = samples[key] ?? (first: now, next: 0)
        guard sample.next < offsets.count, now - sample.first >= offsets[sample.next] else { return }
        sample.next += 1
        samples[key] = sample
        WalletSheetReadinessTrace.emit(event, fields().merging([
            "elapsedSinceFirstObservationSeconds": String(now - sample.first), "sample": sample.next
        ]) { _, new in new })
    }
}
