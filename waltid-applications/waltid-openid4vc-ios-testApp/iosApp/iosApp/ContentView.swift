//
//  ContentView.swift
//  iosApp
//
//  Created by Ivan Pagac on 26/06/2024.
//

import shared
import SwiftUI

struct ContentView: View {
    @State var kid: String = ""
    @State var offer: String = ""
    @State var presentationOffer: String = ""
    
    var body: some View {
        VStack(alignment: .leading) {
            Text("Instructions").font(.title)
            Text("Browse to https://portal.walt.id")
            Text("Generate offers without PIN")
            
            TextField(text: $kid) {
                Label(
                    title: { Text("kid") },
                    icon: { Image(systemName: "42.circle") }
                )
            }
            
            Button("Random kid") {
                kid = UUID().uuidString
            }
            
            Button("Generate key") {
                shared.Platform_iosKt.generateEcKey(kid: kid) { JWK, err in
                    print(JWK)
                    print(err)
                }
            }.disabled(kid.isEmpty)
            
            TextField(text: $offer) {
                Label(
                    title: { Text("paste credential offer uri") },
                    icon: { Image(systemName: "42.circle") }
                )
            }
            Button("Process credential offer") {
                shared.Platform_iosKt.acceptOffer(kid: kid, offerUri: offer, completionHandler: { error in
                    print(error)
                })
            }.disabled(kid.isEmpty || offer.isEmpty)
            
            TextField(text: $presentationOffer) {
                Label(
                    title: { Text("paste presentation offer uri") },
                    icon: { Image(systemName: "42.circle") }
                )
            }
            Button("Process presentation offer") {
                do {
                    let resultUri = try shared.Platform_iosKt.authorize(kid: kid, uri: presentationOffer)
                    print(resultUri)
                } catch {
                    print(error)
                }
                
            }.disabled(kid.isEmpty || presentationOffer.isEmpty)
        }.padding()
        Spacer()
        
    }
}

#Preview {
    ContentView()
}
