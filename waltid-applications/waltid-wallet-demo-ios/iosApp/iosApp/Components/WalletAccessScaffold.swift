import SwiftUI

/// Keyboard avoidance belongs to SwiftUI; one flexible input region serves every PIN journey.
struct WalletAccessScaffold<Header: View, Input: View, Feedback: View, Actions: View>: View {
    @ViewBuilder let header: () -> Header
    @ViewBuilder let input: () -> Input
    @ViewBuilder let feedback: () -> Feedback
    @ViewBuilder let actions: () -> Actions

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    header()
                    Spacer(minLength: 24)
                    input()
                    feedback().frame(maxWidth: .infinity, minHeight: 64, alignment: .top).padding(.top, 12)
                    Spacer(minLength: 24)
                    actions()
                }
                .padding(20)
                .frame(maxWidth: 640)
                .frame(minHeight: geometry.size.height)
                .frame(maxWidth: .infinity)
            }
            .accessScrollDismissesKeyboard()
        }
        .background(Color(uiColor: .systemGroupedBackground))
    }
}

private extension View {
    @ViewBuilder func accessScrollDismissesKeyboard() -> some View {
        if #available(iOS 16, *) { scrollDismissesKeyboard(.interactively) } else { self }
    }
}
