import Foundation

struct ClaimPathExpression {
    enum Segment: Equatable {
        case key(String)
        case index(Int)
        case wildcard
    }

    let segments: [Segment]

    var leafKey: String? {
        segments.reversed().compactMap { segment in
            if case .key(let value) = segment { return value }
            return nil
        }.first
    }

    static func parse(_ rawValue: String) -> ClaimPathExpression {
        var parser = ClaimPathExpressionParser(rawValue: rawValue)
        return parser.parse()
    }
}

private struct ClaimPathExpressionParser {
    private let characters: [Character]
    private var index = 0
    private var segments: [ClaimPathExpression.Segment] = []

    init(rawValue: String) {
        self.characters = Array(rawValue.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    mutating func parse() -> ClaimPathExpression {
        while index < characters.count {
            switch characters[index] {
            case "$", ".", " ", "\n", "\r", "\t":
                index += 1
            case "[":
                addBracketSegment()
            default:
                addKey(readDotSegment())
            }
        }
        return ClaimPathExpression(segments: segments)
    }

    private mutating func addBracketSegment() {
        index += 1
        skipWhitespace()
        let segment: String
        if index < characters.count, characters[index].isQuote {
            segment = readQuotedSegment(quote: characters[index])
        } else {
            segment = readUnquotedBracketSegment()
        }
        skipUntilBracketEnd()
        addToken(segment)
    }

    private mutating func addToken(_ rawSegment: String) {
        let segment = rawSegment.trimmingCharacters(in: .whitespacesAndNewlines)
        if segment.isEmpty {
            return
        }
        if segment == "*" {
            segments.append(.wildcard)
        } else if let intValue = Int(segment) {
            segments.append(.index(intValue))
        } else {
            addKey(segment)
        }
    }

    private mutating func addKey(_ rawSegment: String) {
        let segment = rawSegment.trimmingCharacters(in: .whitespacesAndNewlines)
        if !segment.isEmpty {
            segments.append(.key(segment))
        }
    }

    private mutating func skipWhitespace() {
        while index < characters.count, characters[index].isWhitespace {
            index += 1
        }
    }

    private mutating func skipUntilBracketEnd() {
        while index < characters.count, characters[index] != "]" {
            index += 1
        }
        if index < characters.count {
            index += 1
        }
    }

    private mutating func readDotSegment() -> String {
        let start = index
        while index < characters.count, characters[index] != ".", characters[index] != "[" {
            index += 1
        }
        return String(characters[start..<index])
    }

    private mutating func readUnquotedBracketSegment() -> String {
        let start = index
        while index < characters.count, characters[index] != "]" {
            index += 1
        }
        return String(characters[start..<index])
    }

    private mutating func readQuotedSegment(quote: Character) -> String {
        index += 1
        var segment = ""
        while index < characters.count {
            let character = characters[index]
            if character == "\\", index + 1 < characters.count {
                index += 1
                segment.append(characters[index])
                index += 1
            } else if character == quote {
                index += 1
                return segment
            } else {
                segment.append(character)
                index += 1
            }
        }
        return segment
    }
}

private extension Character {
    var isQuote: Bool {
        self == "'" || self == "\""
    }
}
