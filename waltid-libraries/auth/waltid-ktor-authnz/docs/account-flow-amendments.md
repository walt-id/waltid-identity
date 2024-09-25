# Account flow amendments

Accounts can have amendments set, which are specific
to and applied only on that respective account.

For example:
- Accounts might login with the EmailPass method by default
- Alice wants to increase his security, and thus amends the flow by adding
  the TOTP method for 2FA
- Bob does not want to bother with email and password, and thus replaces her flow
  with the Webauthn method
- Charles has not yet set a password, and thus the flow is set to Email magic link
