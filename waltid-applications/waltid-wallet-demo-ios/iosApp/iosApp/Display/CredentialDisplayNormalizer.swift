import Foundation
import WalletSDK

enum CredentialDisplayNormalizer {

    static func details(for credential: Credential) -> CredentialDetails {
        details(
            id: credential.id,
            title: credential.label ?? credential.format,
            issuer: credential.issuer,
            subject: credential.subject,
            format: credential.format,
            addedAt: credential.addedAt,
            credentialDataJSON: credential.credentialDataJSON
        )
    }

    static func details(for option: PresentationCredentialOption) -> CredentialDetails {
        let parsed = details(
            id: option.selection.id,
            title: option.label ?? option.format,
            issuer: option.issuer,
            subject: option.subject,
            format: option.format,
            addedAt: nil,
            credentialDataJSON: option.credentialDataJSON
        )
        let requestedItems = option.disclosures.enumerated().map { index, disclosure in
            let path = disclosurePath(index: index, disclosure: disclosure)
            return ClaimItem(
                path: path.itemPath,
                pathComponents: path.components,
                label: CredentialDisplayVocabulary.disclosureLabel(
                    name: disclosure.name,
                    path: disclosure.path
                ),
                value: disclosureValue(for: disclosure, path: path),
                rawValue: disclosure.valueJSON,
                roles: CredentialDisplayVocabulary.roles(for: path.components)
            )
        }
        let requestedGroups = requestedItems.isEmpty
            ? []
            : [ClaimGroup(title: CredentialDisplayVocabulary.requestedDisclosuresTitle, items: requestedItems)]

        return CredentialDetails(
            id: parsed.id,
            title: parsed.title,
            issuer: parsed.issuer,
            subject: parsed.subject,
            format: parsed.format,
            addedAt: parsed.addedAt,
            groups: requestedGroups + parsed.groups
        )
    }

    static func transactionDataGroups(for request: PresentationRequestInfo) -> [ClaimGroup] {
        let baseTitles = request.transactionData.map { item in
            item.displayName.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                ?? CredentialDisplayVocabulary.transactionDataTitle
        }
        let titleCounts = baseTitles.reduce(into: [String: Int]()) { counts, title in
            counts[title, default: 0] += 1
        }
        var seenTitles: [String: Int] = [:]

        return request.transactionData.enumerated().map { index, item in
            let baseTitle = baseTitles[index]
            seenTitles[baseTitle, default: 0] += 1
            let title = titleCounts[baseTitle, default: 0] > 1
                ? "\(baseTitle) \(seenTitles[baseTitle, default: 0])"
                : baseTitle

            let typePath = DisplayClaimPath.transactionData(index: index, field: .type)
            var items = claimItems(
                fromJSON: item.detailsJSON,
                pathPrefix: .transactionData(index: index, field: .details),
                fallbackLabel: CredentialDisplayVocabulary.transactionDataLabel(.details)
            )

            items.append(
                ClaimItem(
                    path: typePath.itemPath,
                    pathComponents: typePath.components,
                    label: CredentialDisplayVocabulary.transactionDataLabel(.type),
                    value: .text(item.type),
                    rawValue: item.type,
                    roles: CredentialDisplayVocabulary.roles(for: typePath.components)
                )
            )

            if !item.credentialQueryIDs.isEmpty {
                let queryPath = DisplayClaimPath.transactionData(index: index, field: .credentialQueryIDs)
                let value = item.credentialQueryIDs.joined(separator: ", ")
                items.append(
                    ClaimItem(
                        path: queryPath.itemPath,
                        pathComponents: queryPath.components,
                        label: CredentialDisplayVocabulary.transactionDataLabel(.credentialQueryIDs),
                        value: .text(value),
                        rawValue: value,
                        roles: CredentialDisplayVocabulary.roles(for: queryPath.components)
                    )
                )
            }

            return ClaimGroup(title: title, items: items)
        }
    }

    static func details(
        id: String,
        title: String,
        issuer: String?,
        subject: String?,
        format: String,
        addedAt: Date?,
        credentialDataJSON: String
    ) -> CredentialDetails {
        let rawJSON = credentialDataJSON.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !rawJSON.isEmpty else {
            return CredentialDetails(
                id: id,
                title: title,
                issuer: issuer,
                subject: subject,
                format: format,
                addedAt: addedAt,
                groups: []
            )
        }

        guard case .object(let members) = CredentialDisplayJSONParser.parse(rawJSON) else {
            return CredentialDetails(
                id: id,
                title: title,
                issuer: issuer,
                subject: subject,
                format: format,
                addedAt: addedAt,
                groups: []
            )
        }

        let displayMembers = format == mdocFormat ? members.compactMap(sanitizedMdocMember) : members
        let groupedItems = displayMembers.flatMap { member in
            let path = DisplayClaimPath.topLevel(member.key)
            return claimRows(path: path, label: CredentialDisplayVocabulary.humanizedLabel(member.key), value: member.value)
                .map { row in
                    let displayRow = row.withMdocDisplaySemantics(format: format)
                    return (CredentialDisplayVocabulary.groupKind(for: displayRow.path.components, format: format), displayRow)
                }
        }
        let groups = Dictionary(grouping: groupedItems, by: { $0.0 })
            .sorted { $0.key.order < $1.key.order }
            .map { group, rows in
                ClaimGroup(
                    title: group.title,
                    items: rows
                        .map(\.1)
                        .sorted { lhs, rhs in
                            CredentialDisplayVocabulary.claimPathCompare(lhs.path.components, rhs.path.components, format: format) == .orderedAscending
                        }
                        .map(\.item),
                    initiallyExpanded: group.initiallyExpanded
                )
            }

        return CredentialDetails(
            id: id,
            title: title,
            issuer: issuer,
            subject: subject,
            format: format,
            addedAt: addedAt,
            groups: groups
        )
    }

    private static func claimItem(path: DisplayClaimPath, label: String, value: CredentialDisplayJSONValue) -> ClaimItem {
        ClaimItem(
            path: path.itemPath,
            pathComponents: path.components,
            label: label,
            value: protocolDisplayValue(for: value, path: path) ?? displayValue(for: value, path: path),
            rawValue: rawString(value),
            roles: CredentialDisplayVocabulary.roles(for: path.components)
        )
    }

    private static func claimItems(
        fromJSON rawJSON: String,
        pathPrefix: DisplayClaimPath,
        fallbackLabel: String
    ) -> [ClaimItem] {
        guard let parsed = CredentialDisplayJSONParser.parse(rawJSON) else {
            let trimmed = rawJSON.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? [] : [
                ClaimItem(
                    path: pathPrefix.itemPath,
                    pathComponents: pathPrefix.components,
                    label: fallbackLabel,
                    value: .raw(trimmed),
                    rawValue: trimmed,
                    roles: CredentialDisplayVocabulary.roles(for: pathPrefix.components)
                )
            ]
        }

        guard case .object(let members) = parsed else {
            return [
                claimItem(path: pathPrefix, label: fallbackLabel, value: parsed)
            ]
        }

        return members.flatMap { member in
            let path = pathPrefix.child(member.key)
            return claimRows(path: path, label: CredentialDisplayVocabulary.humanizedLabel(member.key), value: member.value)
                .map(\.item)
        }
    }

    private static func protocolDisplayValue(
        for value: CredentialDisplayJSONValue,
        path: DisplayClaimPath
    ) -> DisplayValue? {
        switch path.components.last {
        case "_sd":
            guard case .array(let values) = value else { return nil }
            return .text(hiddenClaimCommitmentsText(count: values.count))
        case "cnf":
            guard case .object(let members) = value else { return nil }
            return .text(confirmationKeyText(members))
        case "status":
            guard case .object(let members) = value else { return nil }
            return .text(credentialStatusText(members))
        case "credentialStatus", "credential_status":
            guard case .object(let members) = value else { return nil }
            return .text(credentialStatusText(members))
        case "vct":
            guard case .string(let credentialType) = value else { return nil }
            return .text(readableCredentialTypeText(credentialType))
        default:
            return nil
        }
    }

    private static func claimRows(path: DisplayClaimPath, label: String, value: CredentialDisplayJSONValue) -> [ClaimRow] {
        let item = claimItem(path: path, label: label, value: value)
        return flattenObjectForClaimRows(path: path, item: item, value: value)
    }

    private static func flattenObjectForClaimRows(
        path: DisplayClaimPath,
        item: ClaimItem,
        value: CredentialDisplayJSONValue
    ) -> [ClaimRow] {
        guard case .object(let members) = value else {
            if case .object = item.value {
                return flattenDisplayObjectForClaimRows(item)
            }
            return [ClaimRow(path: path, item: item)]
        }
        guard case .object = item.value else {
            return [ClaimRow(path: path, item: item)]
        }

        return members.flatMap { member in
            let childPath = path.child(member.key)
            let childItem = claimItem(path: childPath, label: CredentialDisplayVocabulary.humanizedLabel(member.key), value: member.value)
            let rows = flattenObjectForClaimRows(path: childPath, item: childItem, value: member.value)
            if rows.count == 1,
               case .image = rows[0].item.value,
               rows[0].item.label == CredentialDisplayVocabulary.humanizedLabel(imageWrapperClaimName) {
                return [ClaimRow(path: rows[0].path, item: rows[0].item.relabelled(item.label))]
            }
            return rows
        }
    }

    private static func flattenDisplayObjectForClaimRows(_ item: ClaimItem) -> [ClaimRow] {
        guard case .object(let entries) = item.value else {
            return [
                ClaimRow(
                    path: DisplayClaimPath(itemPath: item.path, components: item.pathComponents),
                    item: item
                )
            ]
        }

        return entries.flatMap { entry in
            let rows = flattenDisplayObjectForClaimRows(entry)
            if rows.count == 1,
               case .image = rows[0].item.value,
               rows[0].item.label == CredentialDisplayVocabulary.humanizedLabel(imageWrapperClaimName) {
                return [ClaimRow(path: rows[0].path, item: rows[0].item.relabelled(item.label))]
            }
            return rows
        }
    }

    private static func displayValue(for value: CredentialDisplayJSONValue, path: DisplayClaimPath) -> DisplayValue {
        switch value {
        case .null:
            return .null
        case .string(let string):
            if let dateText = epochDateStringIfTemporal(value: string, path: path) {
                return .text(dateText)
            }
            return CredentialDisplayValueDecoder.decodedValue(
                for: string,
                path: path,
                renderJSON: { json, jsonPath in displayValue(for: json, path: jsonPath) }
            ) ?? .text(string)
        case .number(let number):
            if let dateText = epochDateStringIfTemporal(value: number, path: path) {
                return .text(dateText)
            }
            return .number(number)
        case .bool(let bool):
            return .bool(bool)
        case .object(let members):
            let items = members.map { member in
                claimItem(path: path.child(member.key), label: CredentialDisplayVocabulary.humanizedLabel(member.key), value: member.value)
            }
            return .object(items)
        case .array(let list):
            if let image = CredentialDisplayValueDecoder.imageDisplayValue(
                for: list,
                roles: CredentialDisplayVocabulary.roles(for: path.components)
            ) {
                return image
            }
            return .list(list.enumerated().map { index, element in
                displayValue(for: element, path: path.indexed(index))
            })
        }
    }

    private static func rawString(_ value: CredentialDisplayJSONValue) -> String? {
        value.rawJSON
    }

    private static func sanitizedMdocMember(_ member: CredentialDisplayJSONMember) -> CredentialDisplayJSONMember? {
        guard case .null = member.value else {
            return CredentialDisplayJSONMember(key: member.key, value: sanitizeMdocValue(member.value))
        }
        return nil
    }

    private static func sanitizeMdocValue(_ value: CredentialDisplayJSONValue) -> CredentialDisplayJSONValue {
        switch value {
        case .object(let members):
            return .object(members.compactMap(sanitizedMdocMember))
        case .array(let values):
            return .array(values.map(sanitizeMdocValue))
        case .string, .number, .bool, .null:
            return value
        }
    }

    private static func disclosurePath(index: Int, disclosure: PresentationDisclosure) -> DisplayClaimPath {
        let leaf = ClaimPathExpression.parse(disclosure.path).leafKey
            ?? disclosure.name?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
            ?? "value"
        return DisplayClaimPath(
            itemPath: ClaimItemPath.topLevel("disclosures").indexedChild(index).child(leaf),
            components: ["disclosures", leaf]
        )
    }

    private static func disclosureValue(
        for disclosure: PresentationDisclosure,
        path: DisplayClaimPath
    ) -> DisplayValue {
        if let parsed = CredentialDisplayJSONParser.parse(disclosure.valueJSON) {
            let parsedValue = displayValue(for: parsed, path: path)
            if case .text = parsedValue,
               let displayValue = disclosure.displayValue?.trimmingCharacters(in: .whitespacesAndNewlines),
               !displayValue.isEmpty {
                return .text(displayValue)
            }
            return parsedValue
        }

        if let displayValue = disclosure.displayValue?.trimmingCharacters(in: .whitespacesAndNewlines),
           !displayValue.isEmpty {
            return .text(displayValue)
        }

        return .raw(disclosure.valueJSON)
    }

    private static func epochDateStringIfTemporal(value: String, path: DisplayClaimPath) -> String? {
        guard CredentialDisplayVocabulary.roles(for: path.components).contains(.temporal),
              let rawEpoch = Int64(value.trimmingCharacters(in: .whitespacesAndNewlines)),
              rawEpoch >= minimumCredibleEpochSeconds else {
            return nil
        }
        let epochSeconds = rawEpoch >= epochMillisecondsThreshold ? rawEpoch / 1_000 : rawEpoch
        return utcDateFormatter.string(from: Date(timeIntervalSince1970: TimeInterval(epochSeconds)))
    }

    private static let utcDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private static let minimumCredibleEpochSeconds: Int64 = 100_000_000
    private static let epochMillisecondsThreshold: Int64 = 10_000_000_000
    private static let imageWrapperClaimName = "elementValue"
    private static let mdocFormat = "mso_mdoc"
}

private extension String {
    var nonEmpty: String? {
        isEmpty ? nil : self
    }
}

private struct ClaimRow {
    let path: DisplayClaimPath
    let item: ClaimItem

    func withMdocDisplaySemantics(format: String) -> ClaimRow {
        guard let semantics = MdocClaimDisplaySemantics.describe(format: format, path: path.components) else {
            return self
        }
        let displayValue: DisplayValue
        switch semantics.valueKind {
        case .bool:
            if case .bool = item.value {
                displayValue = item.value
            } else {
                displayValue = .text("Unsupported value")
            }
        case .binary:
            displayValue = item.value.binaryAvailability
        case .other:
            displayValue = item.value
        }
        return ClaimRow(
            path: path,
            item: item.replacing(label: semantics.label, value: displayValue)
        )
    }
}

private extension DisplayValue {
    var binaryAvailability: DisplayValue {
        let byteCount: Int?
        switch self {
        case .image(_, _, _, let count):
            byteCount = count
        case .list(let values) where !values.isEmpty && values.allSatisfy({ value in
            if case .number = value { return true }
            return false
        }):
            byteCount = values.count
        default:
            byteCount = nil
        }
        let detail = byteCount.map { $0 == 1 ? "1 byte" : "\($0) bytes" }
        return .text(detail.map { "Available, \($0)" } ?? "Available")
    }
}

private func hiddenClaimCommitmentsText(count: Int) -> String {
    switch count {
    case 0: return "No undisclosed claim values"
    case 1: return "1 undisclosed claim value"
    default: return "\(count) undisclosed claim values"
    }
}

private func credentialStatusText(_ members: [CredentialDisplayJSONMember]) -> String {
    if case .object(let statusListMembers)? = members.first(where: { $0.key == "status_list" })?.value {
        let index = statusListMembers.stringOrNumberValue(for: "idx").map { "Status list index \($0)" }
        let uri = statusListMembers.stringOrNumberValue(for: "uri")
        let text = [index, uri]
            .compactMap { $0 }
            .joined(separator: " - ")
        return text.isEmpty ? "Status list credential" : text
    }

    let text = [
        members.stringOrNumberValue(for: "type"),
        members.stringOrNumberValue(for: "id")
    ]
        .compactMap { $0 }
        .joined(separator: " - ")
    return text.isEmpty ? CredentialDisplayJSONValue.object(members).rawJSON : text
}

private func readableCredentialTypeText(_ value: String) -> String {
    guard let readable = CredentialDisplayVocabulary.readableCredentialType(value) else {
        return value
    }
    return "\(readable) (\(value))"
}

private func confirmationKeyText(_ members: [CredentialDisplayJSONMember]) -> String {
    if case .object(let jwkMembers)? = members.first(where: { $0.key == "jwk" })?.value {
        let keyType = jwkMembers.stringValue(for: "kty")
        let curve = jwkMembers.stringValue(for: "crv")
        return ["Key-bound credential", keyType, curve]
            .compactMap { $0 }
            .joined(separator: " - ")
    }

    if let keyID = members.stringValue(for: "kid"), !keyID.isEmpty {
        return "Key-bound credential - \(keyID)"
    }

    return "Key-bound credential"
}

private extension Array where Element == CredentialDisplayJSONMember {
    func stringValue(for key: String) -> String? {
        guard case .string(let value)? = first(where: { $0.key == key })?.value else {
            return nil
        }
        return value
    }

    func stringOrNumberValue(for key: String) -> String? {
        switch first(where: { $0.key == key })?.value {
        case .string(let value), .number(let value):
            return value
        default:
            return nil
        }
    }
}

private extension ClaimItem {
    func relabelled(_ label: String) -> ClaimItem {
        ClaimItem(
            path: path,
            pathComponents: pathComponents,
            label: label,
            value: value,
            rawValue: rawValue,
            roles: roles
        )
    }

    func replacing(label: String, value: DisplayValue) -> ClaimItem {
        ClaimItem(
            path: path,
            pathComponents: pathComponents,
            label: label,
            value: value,
            rawValue: rawValue,
            roles: roles
        )
    }
}

struct CredentialDisplayJSONMember {
    let key: String
    let value: CredentialDisplayJSONValue
}

enum CredentialDisplayJSONValue {
    case object([CredentialDisplayJSONMember])
    case array([CredentialDisplayJSONValue])
    case string(String)
    case number(String)
    case bool(Bool)
    case null

    var rawJSON: String {
        switch self {
        case .object(let members):
            let body = members
                .map { jsonEscapedString($0.key) + ":" + $0.value.rawJSON }
                .joined(separator: ",")
            return "{\(body)}"
        case .array(let values):
            return "[" + values.map(\.rawJSON).joined(separator: ",") + "]"
        case .string(let value):
            return jsonEscapedString(value)
        case .number(let value):
            return value
        case .bool(let value):
            return value ? "true" : "false"
        case .null:
            return "null"
        }
    }
}

enum CredentialDisplayJSONParser {
    static func parse(_ rawJSON: String) -> CredentialDisplayJSONValue? {
        var parser = Parser(rawJSON)
        return try? parser.parse()
    }
}

private struct Parser {
    private let scalars: [UnicodeScalar]
    private var index = 0

    init(_ rawJSON: String) {
        self.scalars = Array(rawJSON.unicodeScalars)
    }

    mutating func parse() throws -> CredentialDisplayJSONValue {
        skipWhitespace()
        let value = try parseValue()
        skipWhitespace()
        guard index == scalars.count else { throw ParseError.invalidJSON }
        return value
    }

    private mutating func parseValue() throws -> CredentialDisplayJSONValue {
        skipWhitespace()
        guard let scalar = current else { throw ParseError.invalidJSON }
        switch scalar {
        case "{": return .object(try parseObject())
        case "[": return .array(try parseArray())
        case "\"": return .string(try parseString())
        case "t":
            try consumeLiteral("true")
            return .bool(true)
        case "f":
            try consumeLiteral("false")
            return .bool(false)
        case "n":
            try consumeLiteral("null")
            return .null
        default:
            return .number(try parseNumber())
        }
    }

    private mutating func parseObject() throws -> [CredentialDisplayJSONMember] {
        try consume("{")
        skipWhitespace()
        if consumeIfPresent("}") {
            return []
        }

        var members: [CredentialDisplayJSONMember] = []
        while true {
            skipWhitespace()
            let key = try parseString()
            skipWhitespace()
            try consume(":")
            let value = try parseValue()
            members.append(CredentialDisplayJSONMember(key: key, value: value))
            skipWhitespace()
            if consumeIfPresent("}") {
                return members
            }
            try consume(",")
        }
    }

    private mutating func parseArray() throws -> [CredentialDisplayJSONValue] {
        try consume("[")
        skipWhitespace()
        if consumeIfPresent("]") {
            return []
        }

        var values: [CredentialDisplayJSONValue] = []
        while true {
            values.append(try parseValue())
            skipWhitespace()
            if consumeIfPresent("]") {
                return values
            }
            try consume(",")
        }
    }

    private mutating func parseString() throws -> String {
        try consume("\"")
        var result = String.UnicodeScalarView()
        while let scalar = current {
            advance()
            if scalar == "\"" {
                return String(result)
            }
            if scalar != "\\" {
                result.append(scalar)
                continue
            }
            guard let escaped = current else { throw ParseError.invalidJSON }
            advance()
            switch escaped {
            case "\"", "\\", "/":
                result.append(escaped)
            case "b":
                result.append(UnicodeScalar(0x08)!)
            case "f":
                result.append(UnicodeScalar(0x0C)!)
            case "n":
                result.append("\n")
            case "r":
                result.append("\r")
            case "t":
                result.append("\t")
            case "u":
                try appendUnicodeEscape(to: &result)
            default:
                throw ParseError.invalidJSON
            }
        }
        throw ParseError.invalidJSON
    }

    private mutating func appendUnicodeEscape(to result: inout String.UnicodeScalarView) throws {
        let value = try readHexScalarValue()
        if (0xD800...0xDBFF).contains(value) {
            guard current == "\\", peek() == "u" else { throw ParseError.invalidJSON }
            advance()
            advance()
            let low = try readHexScalarValue()
            guard (0xDC00...0xDFFF).contains(low) else { throw ParseError.invalidJSON }
            let scalarValue = 0x10000 + ((value - 0xD800) << 10) + (low - 0xDC00)
            guard let scalar = UnicodeScalar(scalarValue) else { throw ParseError.invalidJSON }
            result.append(scalar)
            return
        }
        guard let scalar = UnicodeScalar(value) else { throw ParseError.invalidJSON }
        result.append(scalar)
    }

    private mutating func readHexScalarValue() throws -> UInt32 {
        var value: UInt32 = 0
        for _ in 0..<4 {
            guard let scalar = current, let digit = scalar.jsonHexDigitValue else { throw ParseError.invalidJSON }
            advance()
            value = value * 16 + UInt32(digit)
        }
        return value
    }

    private mutating func parseNumber() throws -> String {
        let start = index
        if current == "-" {
            advance()
        }
        try consumeDigits()
        if current == "." {
            advance()
            try consumeDigits()
        }
        if current == "e" || current == "E" {
            advance()
            if current == "+" || current == "-" {
                advance()
            }
            try consumeDigits()
        }
        guard index > start else { throw ParseError.invalidJSON }
        return String(String.UnicodeScalarView(scalars[start..<index]))
    }

    private mutating func consumeDigits() throws {
        let start = index
        while let scalar = current, scalar.value >= 48, scalar.value <= 57 {
            advance()
        }
        guard index > start else { throw ParseError.invalidJSON }
    }

    private mutating func consumeLiteral(_ literal: String) throws {
        for scalar in literal.unicodeScalars {
            try consume(scalar)
        }
    }

    private mutating func consume(_ expected: UnicodeScalar) throws {
        guard current == expected else { throw ParseError.invalidJSON }
        advance()
    }

    private mutating func consumeIfPresent(_ expected: UnicodeScalar) -> Bool {
        guard current == expected else { return false }
        advance()
        return true
    }

    private mutating func skipWhitespace() {
        while let scalar = current, scalar == " " || scalar == "\n" || scalar == "\r" || scalar == "\t" {
            advance()
        }
    }

    private var current: UnicodeScalar? {
        index < scalars.count ? scalars[index] : nil
    }

    private func peek() -> UnicodeScalar? {
        index + 1 < scalars.count ? scalars[index + 1] : nil
    }

    private mutating func advance() {
        index += 1
    }

    private enum ParseError: Error {
        case invalidJSON
    }
}

private func jsonEscapedString(_ value: String) -> String {
    var result = "\""
    for scalar in value.unicodeScalars {
        switch scalar {
        case "\"": result += "\\\""
        case "\\": result += "\\\\"
        case "\n": result += "\\n"
        case "\r": result += "\\r"
        case "\t": result += "\\t"
        default:
            if scalar.value < 0x20 {
                result += String(format: "\\u%04X", scalar.value)
            } else {
                result.unicodeScalars.append(scalar)
            }
        }
    }
    result += "\""
    return result
}

private extension UnicodeScalar {
    var jsonHexDigitValue: Int? {
        switch value {
        case 48...57: return Int(value - 48)
        case 65...70: return Int(value - 55)
        case 97...102: return Int(value - 87)
        default: return nil
        }
    }
}
