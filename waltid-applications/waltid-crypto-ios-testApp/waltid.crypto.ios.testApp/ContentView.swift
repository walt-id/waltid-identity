//
//  ContentView.swift
//  waltid-crypto-test
//
//  Created by Ivan Pagac on 27/02/2024.
//

//import CryptoKit
import SwiftUI
import waltid_crypto_ios

struct ContentView: View {
    let input: String = """
    {
    "sub": "1234567890",
    "name": "John Doe",
    "iat": 1516239022
    }
    """
    var inputByteArray: KotlinByteArray
    {
        get {
            IosKeyKt.ExportedToByteArray(input, startIndex: 0, endIndex: 0, throwOnInvalidSequence: false)
        }
    }
    
    let appId = "appName"
     
    @State var keyId: String = ""
    
    @State var p256Key: waltid_crypto_ios.Waltid_cryptoKey? = nil
    @State var p256KeysignRawResult: Any? = nil
    @State var p256KeysignJwsResult: String? = nil
    
    @State var rsaKey: waltid_crypto_ios.Waltid_cryptoKey? = nil
    @State var rsasignRawResult: Any? = nil
    @State var rsasignJwsResult: String? = nil
    
    var body: some View {
        ScrollView {
            TextField("Key id", text: $keyId)
            
            GroupBox("RSA") {
                Button("Generate key") {
                    guard !keyId.isEmpty else {
                        print("keyid is empty.")
                        return
                    }
                    
                    do {
                        _ = waltid_crypto_ios.RSAKey.companion.create(kid: keyId, appId: appId, size: 2048)
                        rsaKey = try IosKeys.companion.load(kid: keyId, type: .rsa)
                    } catch {
                        print(error)
                    }
                }
                Button("Public representation") {
                    if let rsaKey {
                        rsaKey.getPublicKeyRepresentation { bytes, err in
                            if let bytes {
                                print(bytes)
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
                Button("Export jwk") {
                    if let rsaKey {
                        rsaKey.exportJWK(completionHandler: { jwk, err in
                            if let jwk {
                                print(jwk)
                            }
                            
                            if let err {
                                print(err)
                            }
                        })
                    }
                }
                
                Button("Export jwk object") {
                    if let rsaKey {
                        rsaKey.exportJWKObject { jwk, err in
                            if let jwk {
                                print(jwk)
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
                
                Button("Export pem") {
                    if let rsaKey {
                        rsaKey.exportPEM { pem, err in
                            if let pem {
                                print(pem)
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
                
                Button("Sign raw") {
                    if let rsaKey {
                        rsaKey.signRaw(plaintext: inputByteArray) { sig, err in
                            if let sig {
                                print(sig)
                                rsasignRawResult = sig
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
                Button("Verify raw") {
                    if let rsaKey, let rsasignRawResult {
                        rsaKey.verifyRaw(signed: rsasignRawResult as! KotlinByteArray, detachedPlaintext: inputByteArray) { result, err in
                            if let result {
                                print(result)
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
                
                Button("Sign jws") {
                    if let rsaKey {
                        rsaKey.signJws(plaintext: inputByteArray, headers:["alg":"RS256"]) { sig, err in
                            if let sig {
                                print(sig)
                                rsasignJwsResult = sig
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
                Button("Verify jws") {
                    if let rsaKey, let rsasignJwsResult {
                        rsaKey.verifyJws(signedJws: rsasignJwsResult) { result, err in
                            if let result {
                                print(result)
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
            }
            GroupBox("secp256r1") {
                Button("Generate key") {
                    guard !keyId.isEmpty else {
                        print("keyid is empty.")
                        return
                    }
                    
                    do {
                        _ = P256Key.companion.create(kid: keyId, appId: appId)
                        p256Key = try IosKeys.companion.load(kid: keyId, type: .secp256r1)
                    } catch {
                        print(error)
                    }
                }
                Button("Public representation") {
                    if let p256Key {
                        p256Key.getPublicKeyRepresentation { bytes, err in
                            if let bytes {
                                print(bytes)
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
                Button("Export jwk") {
                    if let p256Key {
                        p256Key.exportJWK(completionHandler: { jwk, err in
                            if let jwk {
                                print(jwk)
                            }
                            
                            if let err {
                                print(err)
                            }
                        })
                    }
                }
                
                Button("Export jwk object") {
                    if let p256Key {
                        p256Key.exportJWKObject { jwk, err in
                            if let jwk {
                                print(jwk)
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
                
                Button("Export pem") {
                    if let p256Key {
                        p256Key.exportPEM { pem, err in
                            if let pem {
                                print(pem)
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
                
                Button("Sign raw") {
                    if let p256Key {
                        p256Key.signRaw(plaintext: inputByteArray) { sig, err in
                            if let sig {
                                print(sig)
                                p256KeysignRawResult = sig
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
                Button("Verify raw") {
                    if let p256Key, let p256KeysignRawResult {
                        p256Key.verifyRaw(signed: p256KeysignRawResult as! KotlinByteArray, detachedPlaintext: inputByteArray) { result, err in
                            if let result {
                                print(result)
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
                
                Button("Sign jws") {
                    if let p256Key {
                        p256Key.signJws(plaintext: inputByteArray, headers:["alg":"ES256"]) { sig, err in
                            if let sig {
                                print(sig)
                                p256KeysignJwsResult = sig
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
                Button("Verify jws") {
                    if let p256Key, let p256KeysignJwsResult {
                        p256Key.verifyJws(signedJws: p256KeysignJwsResult) { result, err in
                            if let result {
                                print(result)
                            }
                            
                            if let err {
                                print(err)
                            }
                        }
                    }
                }
            }
            
//            GroupBox("Ed25519") {
//                Button("Generate key") {
//                    guard !keyId.isEmpty else {
//                        print("keyid is empty.")
//                        return
//                    }
//                    
//                    do {
//                        
//                        _ = Ed25519Key.companion.create(kid: keyId, appId: appId)
//                        key = IosKeys.companion.load(kid: keyId, type: .ed25519)
//
//                    } catch {
//                        print(error)
//                    }
//                }
//                Button("Public representation") {
//                    if let key {
//                        key.getPublicKeyRepresentation { bytes, err in
//                            if let bytes {
//                                print(bytes)
//                            }
//                            
//                            if let err {
//                                print(err)
//                            }
//                        }
//                    }
//                }
//                Button("Export jwk") {
//                    if let key {
//                        key.exportJWK(completionHandler: { jwk, err in
//                            if let jwk {
//                                print(jwk)
//                            }
//                            
//                            if let err {
//                                print(err)
//                            }
//                        })
//                    }
//                }
//                
//                Button("Export jwk object") {
//                    if let key {
//                        key.exportJWKObject { jwk, err in
//                            if let jwk {
//                                print(jwk)
//                            }
//                            
//                            if let err {
//                                print(err)
//                            }
//                        }
//                    }
//                }
//                
//                Button("Export pem") {
//                    if let key {
//                        key.exportPEM { pem, err in
//                            if let pem {
//                                print(pem)
//                            }
//                            
//                            if let err {
//                                print(err)
//                            }
//                        }
//                    }
//                }
//                
//                Button("Sign raw") {
//                    if let key {
//                        key.signRaw(plaintext: inputByteArray) { sig, err in
//                            if let sig {
//                                print(sig)
//                                signRawResult = sig
//                            }
//                            
//                            if let err {
//                                print(err)
//                            }
//                        }
//                    }
//                }
//                Button("Verify raw") {
//                    if let key, let signRawResult {
//                        key.verifyRaw(signed: signRawResult as! KotlinByteArray, detachedPlaintext: inputByteArray) { result, err in
//                            if let result {
//                                print(result)
//                            }
//                            
//                            if let err {
//                                print(err)
//                            }
//                        }
//                    }
//                }
//                
//                Button("Sign jws") {
//                    if let key {
//                        
//                        key.signJws(plaintext: inputByteArray, headers:["alg:":"EdDSA"]) { sig, err in
//                            if let sig {
//                                print(sig)
//                                signJwsResult = sig
//                            }
//                            
//                            if let err {
//                                print(err)
//                            }
//                        }
//                    }
//                }
//                Button("Verify jws") {
//                    if let key, let signJwsResult {
//                        key.verifyJws(signedJws: signJwsResult) { result, err in
//                            if let result {
//                                print(result)
//                            }
//                            
//                            if let err {
//                                print(err)
//                            }
//                        }
//                    }
//                }
//            }
        }.padding()
    }
}

#Preview {
    ContentView()
}

extension String {
    /// Create `Data` from hexadecimal string representation
    ///
    /// This creates a `Data` object from hex string. Note, if the string has any spaces or non-hex characters (e.g. starts with '<' and with a '>'), those are ignored and only hex characters are processed.
    ///
    /// - returns: Data represented by this hexadecimal string.
    
    var hexadecimal: Data? {
        var data = Data(capacity: count / 2)
        
        let regex = try! NSRegularExpression(pattern: "[0-9a-f]{1,2}", options: .caseInsensitive)
        regex.enumerateMatches(in: self, range: NSRange(startIndex..., in: self)) { match, _, _ in
            let byteString = (self as NSString).substring(with: match!.range)
            let num = UInt8(byteString, radix: 16)!
            data.append(num)
        }
        
        guard data.count > 0 else { return nil }
        
        return data
    }
}
