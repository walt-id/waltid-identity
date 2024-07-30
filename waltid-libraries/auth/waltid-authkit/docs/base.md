# walt.id Auth Kit library

## Model

The walt.id Auth Kit provides a vast set of authentication mechanisms
that can be reused by a Ktor based service.
It provides all features necessary for authentication and
session management, while retaining flexibility for service implementations.

In practice, this means that the Auth Kit works

- as an abstraction layer around various
  authentication mechanisms and protocols (e.g. Username/Email + Password, OAuth2/OIDC, SAML, LDAP, RADIUS, WebAuthn, TOTP)
- distributed session management
- MFA (multi-factor authentication, e.g. Email+Password and TOTP)
- distributed authentication storage
- stored authentication mutation (password reset, add WebAuthn device, etc.)
