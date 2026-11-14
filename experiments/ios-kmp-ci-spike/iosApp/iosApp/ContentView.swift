import SwiftUI
import Shared

struct ContentView: View {
    var body: some View {
        Text(SpikeKt.spikeMessage())
            .padding()
            .accessibilityIdentifier("spikeMessage")
    }
}
