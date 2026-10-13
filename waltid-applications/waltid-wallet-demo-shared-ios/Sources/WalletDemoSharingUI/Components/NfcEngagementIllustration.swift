import SwiftUI

/// Decorative readiness cue, not progress. Its bounds stay fixed while the radio waits.
public struct NfcEngagementIllustration: View {
    private let active: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase

    public init(active: Bool) { self.active = active }

    public var body: some View {
        let animates = active && !reduceMotion && scenePhase == .active
        TimelineView(.animation(minimumInterval: 1 / 30, paused: !animates)) { timeline in
            let pulse = animates ? (1 - cos(timeline.date.timeIntervalSinceReferenceDate * .pi / 1.4)) / 2 : 0.6
            Canvas { context, size in
                context.scaleBy(x: size.width / 216, y: size.height / 176)
                let accent = Color.accentColor
                let outline = Color.secondary
                let surface = Color(.secondarySystemGroupedBackground)
                func rounded(_ rect: CGRect, radius: CGFloat, fill: Color, stroke: Color? = nil, width: CGFloat = 2) {
                    let path = Path(roundedRect: rect, cornerRadius: radius)
                    context.fill(path, with: .color(fill))
                    if let stroke { context.stroke(path, with: .color(stroke), lineWidth: width) }
                }
                func line(_ start: CGPoint, _ end: CGPoint, color: Color, width: CGFloat) {
                    var path = Path(); path.move(to: start); path.addLine(to: end)
                    context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round))
                }
                context.fill(Path(ellipseIn: CGRect(x: 42, y: 18, width: 140, height: 140)), with: .color(accent.opacity(0.06)))
                rounded(CGRect(x: 140, y: 36, width: 54, height: 86), radius: 12, fill: surface, stroke: outline.opacity(0.35))
                rounded(CGRect(x: 152, y: 51, width: 30, height: 19), radius: 4, fill: outline.opacity(0.3))
                context.fill(Path(ellipseIn: CGRect(x: 164, y: 101, width: 6, height: 6)), with: .color(outline.opacity(0.3)))
                rounded(CGRect(x: 40, y: 43, width: 62, height: 108), radius: 13, fill: surface, stroke: accent, width: 2.5)
                line(CGPoint(x: 63, y: 51), CGPoint(x: 79, y: 51), color: accent, width: 3)
                rounded(CGRect(x: 51, y: 78, width: 40, height: 29), radius: 6, fill: accent.opacity(0.15))
                line(CGPoint(x: 58, y: 87), CGPoint(x: 73, y: 87), color: accent.opacity(0.6), width: 3)
                line(CGPoint(x: 62, y: 142), CGPoint(x: 80, y: 142), color: accent, width: 2.5)
                for radius in [15.0, 25.0, 35.0] {
                    var path = Path()
                    path.addArc(center: CGPoint(x: 103, y: 80), radius: radius + pulse * 2,
                        startAngle: .degrees(-40), endAngle: .degrees(40), clockwise: false)
                    context.stroke(path, with: .color(accent.opacity(0.35 + pulse * 0.65)),
                        style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                }
            }
        }
        .frame(width: 216, height: 176)
        .frame(maxWidth: .infinity)
        .accessibilityHidden(true)
    }
}
