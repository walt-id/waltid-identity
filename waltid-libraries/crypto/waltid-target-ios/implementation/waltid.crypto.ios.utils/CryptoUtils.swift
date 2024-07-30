//
//  CryptoUtils.swift
//  waltid.crypto.ios.utils
//
//  Created by Ivan Pagac on 17/06/2024.
//

import CryptoKit
import Foundation
import JOSESwift

@objc
public class HMAC_Operations: NSObject {
    @objc
    public static func sign(body: String, alg: String, key: Data, typ: String, keyId: String? = nil) -> SignResult {
        return OperationsBase<Data>.sign(body: body, alg: alg, key: key, typ: typ)
    }

    @objc
    public static func verify(jws: String, key: Data) -> VerifyResult {
        return OperationsBase<Data>.verify(jws: jws, key: key)
    }
}

@objc
public class DS_Operations: NSObject {
    @objc
    static func sign(body: Data, alg: String, key: SecKey, headers: [String: String]) -> SignResult {
        return OperationsBase<SecKey>.sign(body: body, alg: alg, key: key, headers: headers)
    }

    @objc
    static func sign(body: Data, alg: String, key: SecKey, headersData: Data) -> SignResult {
        return OperationsBase<SecKey>.sign(body: body, alg: alg, key: key, headersData: headersData)
    }

    @objc
    public static func sign(body: String, alg: String, key: SecKey, typ: String, keyId: String? = nil) -> SignResult {
        return OperationsBase<SecKey>.sign(body: body, alg: alg, key: key, typ: typ)
    }

    @objc
    public static func verify(jws: String, key: SecKey) -> VerifyResult {
        return OperationsBase<SecKey>.verify(jws: jws, key: key)
    }
}

@objc
public class SignResult: NSObject {
    @objc
    public var success: Bool {
        errorMessage == nil
    }

    @objc
    public let data: String?

    @objc
    public let errorMessage: String?

    private init(data: String? = nil, errorMessage: String? = nil) {
        self.data = data
        self.errorMessage = errorMessage
    }

    static func with(success data: String) -> SignResult {
        SignResult(data: data)
    }

    static func with(failure message: String) -> SignResult {
        SignResult(errorMessage: message)
    }
}

@objc
public class VerifyResult: NSObject {
    @objc
    public var success: Bool {
        errorMessage == nil
    }

    @objc
    public let isValid: Bool

    @objc
    public let isValidData: Data?

    @objc
    public let errorMessage: String?

    private init(isValid: Bool = false, errorMessage: String? = nil, isValidData: Data? = nil) {
        self.isValid = isValid
        self.errorMessage = errorMessage
        self.isValidData = isValidData
    }

    static func with(isValid: Bool, data: Data?) -> VerifyResult {
        guard isValid else {
            return VerifyResult(isValid: isValid, errorMessage: "Verification failed.")
        }

        return VerifyResult(isValid: isValid, isValidData: data)
    }

    static func with(failure message: String) -> VerifyResult {
        VerifyResult(errorMessage: message)
    }
}

class OperationsBase<TKeyType>: NSObject {
    public static func sign(body: Data, alg: String, key: TKeyType, headersData: Data) -> SignResult {
        do {
            guard let signingAlgorithm = JOSESwift.SignatureAlgorithm(rawValue: alg) else {
                return SignResult.with(failure: "Unknown signature algorithm")
            }

            guard let signer = Signer(signingAlgorithm: signingAlgorithm, key: key) else {
                return SignResult.with(failure: "Could not construct signer.")
            }

            var header = try {
                var h = try JSONSerialization.jsonObject(with: headersData) as! [String: Any]
                h["alg"] = signingAlgorithm.rawValue
                return try JWSHeader(parameters: h)
            }()

            let jws = try JWS(header: header, payload: Payload(body), signer: signer)
            return SignResult.with(success: jws.compactSerializedString)
        } catch {
            return SignResult.with(failure: "Could not perform Sign, reason: \(error.localizedDescription).")
        }
    }

    public static func sign(body: Data, alg: String, key: TKeyType, headers: [String: String]) -> SignResult {
        do {
            guard let signingAlgorithm = JOSESwift.SignatureAlgorithm(rawValue: alg) else {
                return SignResult.with(failure: "Unknown signature algorithm")
            }

            guard let signer = Signer(signingAlgorithm: signingAlgorithm, key: key) else {
                return SignResult.with(failure: "Could not construct signer.")
            }

            let jwsHeaders = headers.merging([ "alg": signingAlgorithm.rawValue ]) { _, new in
                new
            }

            guard let header = try? JWSHeader(parameters: jwsHeaders) else {
                return SignResult.with(failure: "Could not parse header")
            }

            let jws = try JWS(header: header, payload: Payload(body), signer: signer)
            return SignResult.with(success: jws.compactSerializedString)
        } catch {
            return SignResult.with(failure: "Could not perform Sign, reason: \(error.localizedDescription).")
        }
    }

    public static func sign(body payload: String, alg: String, key: TKeyType, typ: String, keyId: String? = nil) -> SignResult {
        do {
            guard let signingAlgorithm = JOSESwift.SignatureAlgorithm(rawValue: alg) else {
                return SignResult.with(failure: "Unknown signature algorithm")
            }

            guard let signer = Signer(signingAlgorithm: signingAlgorithm, key: key) else {
                return SignResult.with(failure: "Could not construct signer.")
            }

            guard let payloadData = payload.data(using: .utf8) else {
                return SignResult.with(failure: "Body not in UTF-8")
            }

            var header = JWSHeader(algorithm: signingAlgorithm)
            header.typ = typ
            if let keyId {
                header.kid = keyId
            }

            let jws = try JWS(header: header, payload: Payload(payloadData), signer: signer)
            return SignResult.with(success: jws.compactSerializedString)
        } catch {
            return SignResult.with(failure: "Could not perform Sign, reason: \(error.localizedDescription).")
        }
    }

    public static func verify(jws: String, key: TKeyType) -> VerifyResult {
        do {
            let jws = try JWS(compactSerialization: jws)

            guard let algorithm = jws.header.algorithm else {
                return VerifyResult.with(failure: "JWS does not contain alg header or was not recognized. This header must be present and be valid.")
            }

            guard let verifier = Verifier(verifyingAlgorithm: algorithm, key: key) else {
                return VerifyResult.with(failure: "Could not construct verifier with the provided key.")
            }

            return VerifyResult.with(isValid: jws.isValid(for: verifier), data: jws.payload.data())
        } catch {
            return VerifyResult.with(failure: "Could not perform Verify, reason: \(error.localizedDescription)")
        }
    }
}

extension Data {
    /// Hexadecimal string representation of `Data` object.

    var hexadecimal: String {
        return map { String(format: "%02x", $0) }
            .joined()
    }
}

@objc
public class SHA256Utils: NSObject {
    @objc
    public static func hash(data: Data) -> Data {
        SHA256.hash(data: data).data
    }
}

extension Digest {
    var bytes: [UInt8] { Array(makeIterator()) }
    var data: Data { Data(bytes) }
}

@objc
public class ECKeyUtils: NSObject {
    @objc static func exportJwk(publicKey: SecKey) throws -> String {
        return try! ECPublicKey(publicKey: publicKey).jsonString()!
    }
    
    @objc static func exportJwk(privateKey: SecKey) throws -> String {
        return try! ECPrivateKey(privateKey: privateKey).jsonString()!
    }

    @objc static func thumbprint(publicKey: SecKey) throws -> String {
        try! ECPublicKey(publicKey: publicKey).thumbprint()
    }
    
    @objc static func thumbprint(privateKey: SecKey) throws -> String {
        try! ECPrivateKey(privateKey: privateKey).thumbprint()
    }

    @objc static func pem(publicKeyRepresentation: Data) -> String {
        return try! P256.Signing.PublicKey(x963Representation: publicKeyRepresentation).pemRepresentation
    }
    
    @objc static func pem(privateKeyRepresentation: Data) -> String {
        return try! P256.Signing.PrivateKey(x963Representation: privateKeyRepresentation).pemRepresentation
    }
}

extension OSStatus: Error {}
extension String: Error {}

// MARK: - RSA

@objc
public class RSAKeyUtils: NSObject {
    
    @objc static func exportJwk(publicKey: SecKey) throws -> String {
        return try! RSAPublicKey(publicKey: publicKey).jsonString()!
    }

    @objc static func thumbprint(publicKey: SecKey) throws -> String {
        let publicKey = try! RSAPublicKey(publicKey: publicKey)
        return try! publicKey.thumbprint()
    }
    
    @objc static func publicJwkToSecKey(jwk: String) throws -> SecKey {
        let publicKey = try! RSAPublicKey(data: jwk.data(using: .utf8)!)
        let secKey = try! publicKey.converted(to: SecKey.self)
        return secKey
    }
}

// MARK: - ed25519

@objc
public class Ed25519KeyUtils: NSObject {
    @objc public static func create(kid: String, appName: String) throws -> String {
        remove(key: kid, appName: appName)

        let addCommand = [
            kSecValueData as String: Curve25519.Signing.PrivateKey().rawRepresentation,
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: kid,
            kSecAttrService as String: appName
        ] as CFDictionary

        switch SecItemAdd(addCommand, nil) {
        case errSecSuccess: return kid
        case let status: do {
                let message = SecCopyErrorMessageString(status, nil)!
                throw NSError(domain: "Ed25519KeyUtils",
                              code: Int(status),
                              userInfo: [NSLocalizedDescriptionKey: "Failed to create key. Reason: \(message)"])
            }
        }
    }

    @objc @discardableResult public static func remove(key: String, appName: String?) -> OSStatus {
        var deleteQuery = [
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecReturnAttributes as String: true,
            kSecClass as String: kSecClassGenericPassword
        ] as [String:Any]
        
        if let appName {
            deleteQuery[kSecAttrService as String] = appName
            
        }
        
        return SecItemDelete(deleteQuery as CFDictionary)
    }

    @objc public static func load(key: String) throws
    {
        _ = try privateKey(kid: key)
    }

    static func privateKey(kid: String) throws -> Curve25519.Signing.PrivateKey {
        let data = try retrieveData(forKey: kid)
        return try Curve25519.Signing.PrivateKey(rawRepresentation: data)
    }

    static func retrieveData(forKey key: String) throws -> Data {
        let query = [
            kSecAttrAccount as String: key,
            kSecReturnData: true,
            kSecReturnAttributes: true,
            kSecClass as String: kSecClassGenericPassword
        ] as CFDictionary

        var result: AnyObject?
        let resultCode = SecItemCopyMatching(query, &result)

        guard resultCode == errSecSuccess else {
            let message = SecCopyErrorMessageString(resultCode, nil)!
            throw NSError(domain: "Ed25519KeyUtils",
                          code: Int(resultCode),
                          userInfo: [NSLocalizedDescriptionKey: "Failed to retrieve data. Reason: \(message)"])
        }

        let dic = result as! NSDictionary

        return dic[kSecValueData] as! Data
    }
    @objc static func publicRawRepresentation(kid: String) throws -> Data {
        try privateKey(kid: kid).publicKey.rawRepresentation
    }
    
    @objc static func privateRawRepresentation(kid: String) throws -> Data {
        try privateKey(kid: kid).rawRepresentation
    }
    
    @objc static func signRaw(kid: String, plain: Data) throws -> Data {
        return try privateKey(kid: kid).signature(for: plain).data()
    }
    
    @objc static func verifyRaw(kid: String, signature: Data, data: Data) throws -> VerifyResult{
        do {
            guard try privateKey(kid: kid).publicKey.isValidSignature(signature, for: data) else {
                return .with(isValid: false, data: nil)
            }
            
            return .with(isValid: true, data: data)
        } catch {
            return .with(failure: error.localizedDescription)
        }
    }
    
    @objc static func verifyRaw(publicKeyRaw: Data, signature: Data, data: Data) throws -> VerifyResult{
        do {
            guard try Curve25519.Signing.PublicKey(rawRepresentation: publicKeyRaw).isValidSignature(signature, for: data) else {
                return .with(isValid: false, data: nil)
            }
            
            return .with(isValid: true, data: data)
        } catch {
            return .with(failure: error.localizedDescription)
        }
    }
}
