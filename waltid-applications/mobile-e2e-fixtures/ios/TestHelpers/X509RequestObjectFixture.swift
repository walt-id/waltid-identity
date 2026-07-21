import Foundation

public enum X509RequestObjectFixture {
    public static let clientID = "x509_san_dns:verifier.example.com"

    /// Test-only CA for the leaf in `signedRequestObject`, valid until July 2036.
    public static let trustAnchorPEM = """
    -----BEGIN CERTIFICATE-----
    MIIB4DCCAYegAwIBAgIUA/9XuKeSJXtWUSdtBGzaKnmVHrMwCgYIKoZIzj0EAwIw
    PjEqMCgGA1UEAwwhd2FsdC5pZCBBcHAtSG9zdGVkIFg1MDkgVGVzdCBSb290MRAw
    DgYDVQQKDAd3YWx0LmlkMB4XDTI2MDcyMTEwMjcwMVoXDTM2MDcxODEwMjcwMVow
    PjEqMCgGA1UEAwwhd2FsdC5pZCBBcHAtSG9zdGVkIFg1MDkgVGVzdCBSb290MRAw
    DgYDVQQKDAd3YWx0LmlkMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEP2Iu55PO
    zlwrg/MTYpLwbFwvtpdpx68WsA6l46EBHjfwquwQlS0Mqd5LvwBzspFULRY6nNi5
    RneWG6WhTiEQBKNjMGEwHQYDVR0OBBYEFAyoLc/HVdN+lzUziwIT+C4FNpBBMB8G
    A1UdIwQYMBaAFAyoLc/HVdN+lzUziwIT+C4FNpBBMA8GA1UdEwEB/wQFMAMBAf8w
    DgYDVR0PAQH/BAQDAgEGMAoGCCqGSM49BAMCA0cAMEQCIDbF5GL7Dk95vP7X+J3v
    X/4whS7wEaP7fK89tFgEGUTUAiByPxGxtjKLmEdPRyOUR3laRgXPmnV+HNyc8rL3
    3Fk2oA==
    -----END CERTIFICATE-----
    """

    /// Fixed ES256 Request Object with a matching `x5c` leaf, valid until January 2036.
    public static let signedRequestObject = [
        "eyJhbGciOiJFUzI1NiIsInR5cCI6Im9hdXRoLWF1dGh6LXJlcStqd3QiLCJ4NWMiOlsiTUlJQjlEQ0NBWm1nQXdJQkFnSVVWQkF2WUFsczUxWlViaTZTN0d0S21jOWJ5S0l3Q2dZSUtvWkl6ajBFQXdJd1BqRXFNQ2dHQTFVRUF3d2hkMkZzZEM1cFpDQkJjSEF0U0c5emRHVmtJRmcxTURrZ1ZHVnpkQ0JTYjI5ME1SQXdEZ1lEVlFRS0RBZDNZV3gwTG1sa01CNFhEVEkyTURjeU1URXdNamN3TVZvWERUTTJNRGN4TnpFd01qY3dNVm93TVRFZE1Cc0dBMVVFQXd3VWRtVnlhV1pwWlhJdVpYaGhiWEJzWlM1amIyMHhFREFPQmdOVkJBb01CM2RoYkhRdWFXUXdXVEFUQmdjcWhrak9QUUlCQmdncWhrak9QUU1CQndOQ0FBUzB3TnJ1QW4wS1FMQTR3Z0RqVHR0NmFBTTE0Z3Y4bmZhZEpLN0RGZzV0ZDRnSVNudG5DVGtDS3JIeU9TL08veGx0d0J5WXdlZ3ZQQlNwdklaa0UvTXJvNEdCTUg4d0RBWURWUjBUQVFIL0JBSXdBREFPQmdOVkhROEJBZjhFQkFNQ0I0QXdId1lEVlIwUkJCZ3dGb0lVZG1WeWFXWnBaWEl1WlhoaGJYQnNaUzVqYjIwd0hRWURWUjBPQkJZRUZGbEdUdXRub0laZTR3YkFlZ01adlpCb2d3TUxNQjhHQTFVZEl3UVlNQmFBRkF5b0xjL0hWZE4rbHpVeml3SVQrQzRGTnBCQk1Bb0dDQ3FHU000OUJBTUNBMGtBTUVZQ0lRQzFVaitQVmtJTnNxUDFZc21JWTY4a0lLc2ZzSjFsTUN3cFBSeFNUWVVmR1FJaEFKOENic0g3cTVKa0picHN5UTlWN3VZQlc4c21XSzg3T3JuRFJkZk0wUWVLIl19",
        ".eyJhdWQiOiJodHRwczovL3NlbGYtaXNzdWVkLm1lL3YyIiwiZXhwIjoyMDgyNzU4NDAwLCJjbGllbnRfaWQiOiJ4NTA5X3Nhbl9kbnM6dmVyaWZpZXIuZXhhbXBsZS5jb20iLCJyZXNwb25zZV90eXBlIjoidnBfdG9rZW4iLCJyZXNwb25zZV9tb2RlIjoiZGlyZWN0X3Bvc3QiLCJyZXNwb25zZV91cmkiOiJodHRwczovL3ZlcmlmaWVyLmV4YW1wbGUuY29tL3Jlc3BvbnNlIiwibm9uY2UiOiJhcHAtaG9zdGVkLXg1MDktdGVzdCIsInN0YXRlIjoiYXBwLWhvc3RlZC14NTA5LXRlc3QiLCJkY3FsX3F1ZXJ5Ijp7ImNyZWRlbnRpYWxzIjpbeyJpZCI6InRlc3RfY3JlZGVudGlhbCIsImZvcm1hdCI6ImRjK3NkLWp3dCIsIm1ldGEiOnsidmN0X3ZhbHVlcyI6WyJ1cm46ZXhhbXBsZTp0ZXN0Il19fV0sImNyZWRlbnRpYWxfc2V0cyI6W3sib3B0aW9ucyI6W1sidGVzdF9jcmVkZW50aWFsIl1dLCJyZXF1aXJlZCI6ZmFsc2V9XX0sImNsaWVudF9tZXRhZGF0YSI6e319",
        ".MscPu_lMJZURFAel1Myr2Lhi5SFgCKBjX0yO529dBsnVtV4tOq12ADgRz8twZ60KNgcToPcR2uuYse5ghgisrw",
    ].joined()

    public static let authorizationRequestURL: URL = {
        var components = URLComponents(string: "openid4vp://authorize")!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: clientID),
            URLQueryItem(name: "request", value: signedRequestObject),
        ]
        return components.url!
    }()
}
