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
            Key_iosKt.ExportedToByteArray(input, startIndex: 0, endIndex: 0, throwOnInvalidSequence: false)
        }
    }
     
    @State var keyId: String = ""
    
    @State var p256Key: waltid_crypto_ios.Key? = nil
    @State var p256KeysignRawResult: Any? = nil
    @State var p256KeysignJwsResult: String? = nil
    
    @State var p256jwk: String = ""
    
    var body: some View {
        ScrollView {
            TextField("Key id", text: $keyId)
            Button("Random KID") {
                keyId = UUID().uuidString
            }
            
            GroupBox("iOS Keychain secp256r1") {
                Button("Generate key") {
                    guard !keyId.isEmpty else {
                        print("keyid is empty.")
                        return
                    }
                    
                    do {
                        _ = try IosKey.companion.create(kid: keyId, type: .secp256r1)
                        p256Key = try IosKey.companion.load(kid: keyId, type: .secp256r1)
                    } catch {
                        print(error)
                    }
                }
                Button("Private Key - Public representation") {
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
                
                Button("Public Key - Public representation") {
                    if let p256Key {
                        p256Key.getPublicKey { key, err in
                            if let err {
                                return print(err)
                            }
                            
                            if let key {
                                key.getPublicKeyRepresentation { bytes, err in
                                    if let bytes {
                                        print(bytes)
                                    }
                                    
                                    if let err {
                                        print(err)
                                    }
                                }
                            }
                        }
                    }
                }
                Button("Private Key - Export jwk") {
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
                
                Button("Public Key - Export jwk") {
                    if let p256Key {
                        p256Key.getPublicKey { key, err in
                            if let err {
                                return print(err)
                            }
                            
                            if let key {
                                key.exportJWK(completionHandler: { jwk, err in
                                    if let jwk {
                                        print(jwk)
                                        p256jwk = jwk
                                    }
                                    
                                    if let err {
                                        print(err)
                                    }
                                })
                            }
                        }
                    }
                }
                
                Button("Private Key - Export jwk object") {
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
                
                Button("Private Key - Export pem") {
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
                
                Button("Public Key - Export pem") {
                    if let p256Key {
                        
                        p256Key.getPublicKey { key, err in
                            if let err {
                                return print(err)
                            }
                            
                            if let key {
                                key.exportPEM { pem, err in
                                    if let pem {
                                        print(pem)
                                    }
                                    
                                    if let err {
                                        print(err)
                                    }
                                }
                            }
                        }
                    }
                }
                
                Button("Private Key - Sign raw") {
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
                
                Button("Public Key - Verify raw") {
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
                
                
                Button("Private Key - Sign jws") {
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
                
                Button("Public Key - Verify jws") {
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
            
            GroupBox("JWK secp256r1") {
                TextField("JWK", text: $p256jwk)
                Button("Public Key - Export pem") {
                    JWKKey(jwk: p256jwk).exportPEM { result, err in
                        if let result {
                            print(result)
                        }
                        
                        if let err {
                            print(err)
                        }
                    }
                }.disabled(p256jwk.isEmpty)
                
                Button("Public Key - Verify jws") {
                    JWKKey(jwk: p256jwk).verifyJws(signedJws: p256KeysignJwsResult!) { result, err in
                        if let result {
                            print(result)
                        }
                        
                        if let err {
                            print(err)
                        }
                    }
                }.disabled(p256jwk.isEmpty || p256KeysignJwsResult?.isEmpty == true)
                
            }
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
