import Foundation

public enum X509RequestObjectFixture {
    /// SHA-256 Base64URL hash of the leaf certificate in `signedRequestObject`.
    public static let clientID = "x509_hash:IT2EMOaIMUTx1zj0yteLZyOgkXF6Siw2t_K42tsKT_I"

    /// Test-only CA for the leaf in `signedRequestObject`, valid until July 2036.
    public static let trustAnchorPEM = """
    -----BEGIN CERTIFICATE-----
    MIIB6zCCAZGgAwIBAgIUSFgRXRcRirEjuPvPM38K5ZGrPpcwCgYIKoZIzj0EAwIw
    QzEvMC0GA1UEAwwmd2FsdC5pZCBBcHAtSG9zdGVkIFg1MDkgSGFzaCBUZXN0IFJv
    b3QxEDAOBgNVBAoMB3dhbHQuaWQwHhcNMjYwNzI3MDkyMDIwWhcNMzYwNzI0MDky
    MDIwWjBDMS8wLQYDVQQDDCZ3YWx0LmlkIEFwcC1Ib3N0ZWQgWDUwOSBIYXNoIFRl
    c3QgUm9vdDEQMA4GA1UECgwHd2FsdC5pZDBZMBMGByqGSM49AgEGCCqGSM49AwEH
    A0IABFqSCpC466BkSl98zsxgt63FTIjBltCCxhDCHcTZ1+T8ljrbYGHxEVWAiq/T
    XFvAE971gsSm/3lZSgE0lk5pfrijYzBhMB0GA1UdDgQWBBTk0sEWqbKG1u+e8nSa
    +01kr6XitDAfBgNVHSMEGDAWgBTk0sEWqbKG1u+e8nSa+01kr6XitDAPBgNVHRMB
    Af8EBTADAQH/MA4GA1UdDwEB/wQEAwIBBjAKBggqhkjOPQQDAgNIADBFAiBPP37V
    E9U0V2Uax6E9uI00qAtm+feze/eCEtuMs3fRlwIhAP96ce+J+6kuWRvUSZPYISuf
    2JhW6xQPYQbAK6L6nIeR
    -----END CERTIFICATE-----
    """

    /// Fixed ES256 `x509_hash` Request Object with a matching `x5c` leaf.
    public static let signedRequestObject = [
        "eyJhbGciOiJFUzI1NiIsInR5cCI6Im9hdXRoLWF1dGh6LXJlcStqd3QiLCJ4NWMiOlsiTUlJQjdUQ0NBWktnQXdJQkFnSVVMQ3QwbGllc2tseVpnMGxsLyt2M0NVWXAwQ2N3Q2dZSUtvWkl6ajBFQXdJd1F6RXZNQzBHQTFVRUF3d21kMkZzZEM1cFpDQkJjSEF0U0c5emRHVmtJRmcxTURrZ1NHRnphQ0JVWlhOMElGSnZiM1F4RURBT0JnTlZCQW9NQjNkaGJIUXVhV1F3SGhjTk1qWXdOekkzTURreU1ESXdXaGNOTXpZd056STBNRGt5TURJd1dqQkhNVE13TVFZRFZRUUREQ3AzWVd4MExtbGtJRUZ3Y0MxSWIzTjBaV1FnV0RVd09TQklZWE5vSUZSbGMzUWdWbVZ5YVdacFpYSXhFREFPQmdOVkJBb01CM2RoYkhRdWFXUXdXVEFUQmdjcWhrak9QUUlCQmdncWhrak9QUU1CQndOQ0FBVEk1c2VTWGRSRUZnS1lLWnl5SVU4RStqajRjaDdoQUlyanA2YUl6eXBhQ2FvUC9XNmM3TGMwR04rY3o4RTF1YU1POFZjdmNGN1ZQUE9lUDkxWDAzWGlvMkF3WGpBTUJnTlZIUk1CQWY4RUFqQUFNQTRHQTFVZER3RUIvd1FFQXdJSGdEQWRCZ05WSFE0RUZnUVVTZUlNb05saUdQY29aUG1nT0Z6MlhTYUJqM3N3SHdZRFZSMGpCQmd3Rm9BVTVOTEJGcW15aHRidm52SjBtdnROWksrbDRyUXdDZ1lJS29aSXpqMEVBd0lEU1FBd1JnSWhBTjBUVGhlZW1aa1pYUk5jSFhUdENjaVJOc0o5czBWckpQR1J2V2I2UnlGU0FpRUEwTHhla09hK2pxRjhKTHArbzNXNzBMaDlCY253ZDRzRkFuQWJ5WFRNUHFFPSJdfQ",
        ".eyJhdWQiOiJodHRwczovL3NlbGYtaXNzdWVkLm1lL3YyIiwiZXhwIjoyMDgyNzU4NDAwLCJjbGllbnRfaWQiOiJ4NTA5X2hhc2g6SVQyRU1PYUlNVVR4MXpqMHl0ZUxaeU9na1hGNlNpdzJ0X0s0MnRzS1RfSSIsInJlc3BvbnNlX3R5cGUiOiJ2cF90b2tlbiIsInJlc3BvbnNlX21vZGUiOiJkaXJlY3RfcG9zdCIsInJlc3BvbnNlX3VyaSI6Imh0dHBzOi8vdmVyaWZpZXIuZXhhbXBsZS5jb20vcmVzcG9uc2UiLCJub25jZSI6ImFwcC1ob3N0ZWQteDUwOS1oYXNoLXRlc3QiLCJzdGF0ZSI6ImFwcC1ob3N0ZWQteDUwOS1oYXNoLXRlc3QiLCJkY3FsX3F1ZXJ5Ijp7ImNyZWRlbnRpYWxzIjpbeyJpZCI6InRlc3RfY3JlZGVudGlhbCIsImZvcm1hdCI6ImRjK3NkLWp3dCIsIm1ldGEiOnsidmN0X3ZhbHVlcyI6WyJ1cm46ZXhhbXBsZTp0ZXN0Il19fV0sImNyZWRlbnRpYWxfc2V0cyI6W3sib3B0aW9ucyI6W1sidGVzdF9jcmVkZW50aWFsIl1dLCJyZXF1aXJlZCI6ZmFsc2V9XX0sImNsaWVudF9tZXRhZGF0YSI6e319",
        ".2qQUOrCDfL2SVBHa4z-KbkhjrzRFUft-jto17Mq3p4XhSyUOhlcl_FQlGNGJLHrLri06MGnVoO8dM8mCUofRPg",
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
