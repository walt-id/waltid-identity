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
    @objc static func exportJwt(publicKey: SecKey) throws -> String {
        let publicKey = try! ECPublicKey(publicKey: publicKey)
        return publicKey.jsonString()!
    }

    @objc static func thumbprint(publicKey: SecKey) throws -> String {
        let publicKey = try! ECPublicKey(publicKey: publicKey)
        return try! publicKey.thumbprint()
    }

    @objc static func pem(publicKeyRepresentation: Data) -> String {
        print(publicKeyRepresentation.hexadecimal)

        return try! P256.Signing.PublicKey(x963Representation: publicKeyRepresentation).pemRepresentation
    }
}

extension OSStatus: Error {}
extension String: Error {}

// MARK: - RSA

@objc
public class RSAKeyUtils: NSObject {
    @objc static func exportJwt(publicKey: SecKey) throws -> String {
        let publicKey = try! RSAPublicKey(publicKey: publicKey)
        return publicKey.jsonString()!
    }

    @objc static func thumbprint(publicKey: SecKey) throws -> String {
        let publicKey = try! RSAPublicKey(publicKey: publicKey)
        return try! publicKey.thumbprint()
    }
}
