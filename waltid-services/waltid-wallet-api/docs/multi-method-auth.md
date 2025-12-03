# Example: Multiple authentication methods

## Configuration

`_features.conf`:
```hocon
enabledFeatures = [
    ktor-authnz, # New waltid-ktor-authnz backed authentication system.
                   # This requires enabling environment variable NUXT_PUBLIC_AUTH_USE_KTORAUTHNZ=true in the frontend.
                   # Configured in `ktor-authnz.conf`
                   # Disable legacy auth for this (add "auth" in disabledFeatures list).
    # dev-mode # Enabling developer config from file `dev-mode.conf`
    # ...
]
disabledFeatures = [
    auth   # Legacy auth, configured in `auth.conf`
]
```

`ktor-authnz.conf`:
```hocon
# Will secure login cookies with `Secure` context, enable HTTS and HTTP->HTTPS redirect
requireHttps = false


# Provide pepper to use for additional password salting (unique string for your deployment,
# has to be shared between instances).
pepper = "waltid"

# Hash algorithm to use for passwords for signing.
# You can choose from algorithms like: ARGON2, PBKDF2, PBKDF2_COMPRESSED, BCRYPT, SCRYPT, BALLON_HASHING, MESSAGE_DIGEST, NONE
hashAlgorithm = ARGON2

# Configure the Auth Flow (refer to: waltid-ktor-authnz)
authFlows = [
    {
        method: email
        expiration: "7d" # optional: Set expiration time for login tokens, e.g. a week
        success: true # Auth flow ends successfully with this step
    },
    {
        method: "oidc",
        config: {
            openIdConfigurationUrl: "http://localhost:8080/realms/master/.well-known/openid-configuration",
            clientId: "waltid_ktor_authnz",
            clientSecret: "fzYFC6oAgbjozv8NoaXuOIfPxmT4XoVM",
            pkceEnabled: true,
            callbackUri: "http://wallet.localhost:7001/wallet-api/auth/account/oidc/callback",
            redirectAfterLogin: "http://wallet.localhost:7001/wallet-api/auth/oidc-callback-frontend"
        },
        success: true
    }
]

cookieDomain = null

# If you previously used other (older) password hash algorithms, you
# can use this function to migrate old hashes to new hash algorithms. This
# works at login-time: When a user logs in with a password that uses a hash algorithm
# on this list, the password will be re-hashed in the specified replacement algorithm.
# If null is used as hash algorithm selector, all algorithms expect for the target
# algorithm will be converted automatically.
hashMigrations = {
    MESSAGE_DIGEST: ARGON2 # E.g.: Convert all the MD5 hashes to Argon2 hashes
}

# Setup how to issue and verify tokens
# Supported:
# - STORE_IN_MEMORY: In memory token store (single-instance, no configuration necessary)
# - STORE_VALKEY: Store in Redis/Valkey/Redict/KeyDB (multi-instance, distributed logout supported) - configure Redis/Valkey/KeyDB instance below
# - JWT: Tokens as stateless JWT (multi-instance, no logout support as it is stateless!) - configure keys below
tokenType = STORE_VALKEY

# --- Required for JWK tokens:

# Key (all waltid-crypto supported) to sign login token - has to be key allowing signing (private key)
# signingKey = {"type": "jwk", "jwk": {"kty": "OKP", "d": "z8Lk85rAtfv2RJN_cD_-9nqHHwKTlTQ5_I53LcsHjC4", "use": "sig", "crv": "Ed25519", "x": "Ew76rQJ9gPHCOBOwJlf__Il5IjgSAc3bQ_a8psd-F3E", "alg": "EdDSA"}}

# Key (all waltid-crypto supported) to verify incoming login tokens - public key is ok.
# verificationKey = {"type": "jwk", "jwk": {"kty": "OKP", "d": "z8Lk85rAtfv2RJN_cD_-9nqHHwKTlTQ5_I53LcsHjC4", "use": "sig", "crv": "Ed25519", "x": "Ew76rQJ9gPHCOBOwJlf__Il5IjgSAc3bQ_a8psd-F3E", "alg": "EdDSA"}}

# --- Required for Token Store tokens (opaque tokens):

# - Host:
# valkeyUnixSocket = "..."
valkeyHost = "127.0.0.1"
valkeyPort = 6379

# - Authentication: (if server auth is enabled)
# valkeyAuthUsername = "" # optional
# valkeyAuthPassword = ""

# - Expiration of tokens:
valkeyRetention = "7d"
```

## Request flow:

### User 1 (logs in via user/pass)
```shell
curl -X 'GET' \
  'http://wallet.localhost:7001/wallet-api/wallet/accounts/wallets' \
  -H 'accept: application/json'
```
```text
Unauthorized (NoCredentials)
```

```shell
curl -X 'POST' \
  'http://wallet.localhost:7001/wallet-api/auth/account/emailpass' \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
  "email": "my@test.com",
  "password": "mytest"
}'
```
```json
{
  "session_id": "8ba6a134-5615-43f1-a4c7-4976f3a2aee6",
  "status": "SUCCESS",
  "token": "be49e8a3-31d0-4c8a-ba13-2ef73988336a",
  "expiration": "2025-12-04T12:12:18.043226885Z"
}
```

(implicit cookie enabled [not seen in curl command]:)
```shell
curl -X 'GET' \
  'http://wallet.localhost:7001/wallet-api/wallet/accounts/wallets' \
  -H 'accept: application/json'
```
```json
{
  "account": "9d04abd2-16c8-4c3c-9b9d-3c1f16dc3280",
  "wallets": [
    {
      "id": "052ed861-e458-4f86-b49e-bd04bd907efc",
      "name": "Wallet of Mytest",
      "createdOn": "2025-11-27T11:59:07.966Z",
      "addedOn": "2025-11-27T11:59:07.969Z",
      "permission": "ADMINISTRATE"
    }
  ]
}
```

### User 2 (logs in via OIDC)
```shell
curl -X 'GET' \
  'http://wallet.localhost:7001/wallet-api/wallet/accounts/wallets' \
  -H 'accept: application/json'
```
```text
Unauthorized (NoCredentials)
```
```shell
curl -X 'GET' \
  'http://wallet.localhost:7001/wallet-api/auth/account/oidc/auth' \
  -H 'accept: */*'
```
```json
{
  "session_id": "bf500c7a-8401-4020-9a9f-7d132f7c2dda",
  "status": "CONTINUE_NEXT_STEP",
  "current_method": "oidc",
  "next_method": [
    "oidc"
  ],
  "next_step": {
    "type": "redirect",
    "url": "http://localhost:8080/realms/master/protocol/openid-connect/auth?response_type=code&scope=openid+profile+email&client_id=waltid_ktor_authnz&redirect_uri=http%3A%2F%2Fwallet.localhost%3A7001%2Fwallet-api%2Fauth%2Faccount%2Foidc%2Fcallback&state=s_xIX_a-oaaQnUr0i_nQti_js4Q7TYz99qe_KzGmWzw&nonce=lez3oVogZb72JiQI_Tr5SIiah0M8KcwvZcJMn4BXeWQ&code_challenge=ccVEufDCvHyhEqXVHTTuXMDRTGXUWykp964b92WFyvY&code_challenge_method=S256"
  },
  "next_step_description": "The OIDC method requires you to open the Authentication URL in your web browser, and follow the steps of your Identity Provider from there. Please go ahead and open the authentication URL \"http://localhost:8080/realms/master/protocol/openid-connect/auth?response_type=code&scope=openid+profile+email&client_id=waltid_ktor_authnz&redirect_uri=http%3A%2F%2Fwallet.localhost%3A7001%2Fwallet-api%2Fauth%2Faccount%2Foidc%2Fcallback&state=s_xIX_a-oaaQnUr0i_nQti_js4Q7TYz99qe_KzGmWzw&nonce=lez3oVogZb72JiQI_Tr5SIiah0M8KcwvZcJMn4BXeWQ&code_challenge=ccVEufDCvHyhEqXVHTTuXMDRTGXUWykp964b92WFyvY&code_challenge_method=S256\" in your web browser.",
  "next_step_informational_message": "The \"oidc\" authentication method requires multiple-steps for authentication. Follow the steps in `next_step` (AuthSessionNextStepRedirectData) to complete the authentication method."
}
```

(user-agent visits `http://localhost:8080/realms/master/protocol/openid-connect/auth?response_type=code&scope=openid+profile+email&client_id=waltid_ktor_authnz&redirect_uri=http%3A%2F%2Fwallet.localhost%3A7001%2Fwallet-api%2Fauth%2Faccount%2Foidc%2Fcallback&state=s_xIX_a-oaaQnUr0i_nQti_js4Q7TYz99qe_KzGmWzw&nonce=lez3oVogZb72JiQI_Tr5SIiah0M8KcwvZcJMn4BXeWQ&code_challenge=ccVEufDCvHyhEqXVHTTuXMDRTGXUWykp964b92WFyvY&code_challenge_method=S256`)
(user-agent logs in, and is redirected)

```shell
curl -X 'GET' \
  'http://wallet.localhost:7001/wallet-api/wallet/accounts/wallets' \
  -H 'accept: application/json'
```
```json
{
  "account": "e44c92e2-8642-4b83-8c62-0508b111f6bd",
  "wallets": [
    {
      "id": "d81ee307-f46d-4cf7-a47f-a8da84676686",
      "name": "Wallet of 412cf56f-f85a-4247-91a6-f538867e2470",
      "createdOn": "2025-11-27T12:03:13.517Z",
      "addedOn": "2025-11-27T12:03:13.520Z",
      "permission": "ADMINISTRATE"
    }
  ]
}
```

### Data that gets stored for this scenario:

#### AuthnzAccountIdentifiers

| id                                   | user\_id                             | identifier                                                                                        |
|:-------------------------------------|:-------------------------------------|:--------------------------------------------------------------------------------------------------|
| e4a6eae4-fb87-42ea-8b48-7cc6b3337744 | 9d04abd2-16c8-4c3c-9b9d-3c1f16dc3280 | my@test.com                                                                                       |
| 6642c013-8f80-44b0-82ee-3fc67a02f2aa | e44c92e2-8642-4b83-8c62-0508b111f6bd | {"issuer":"http://localhost:8080/realms/master","subject":"412cf56f-f85a-4247-91a6-f538867e2470"} |

#### AuthnzStoredData

| id                                   | account\_id                          | method | data                                                                                                                                                                                                       |
|:-------------------------------------|:-------------------------------------|:-------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 91a37434-7248-4cc4-a4f7-93ce635bf900 | 9d04abd2-16c8-4c3c-9b9d-3c1f16dc3280 | email  | {"type":"email","passwordHash":"ARGON2/$argon2id$v=19$m=15360,t=2,p=1$ogkI3OKqOC5GguN/CPzad7MXYTdqgvwJulqb6/Pv7Awn3sbnUfbG4cdQtZLI/21V0fuaNB5KOIXby+RKhHgbfA$uLr4f8OT+l7VZ5Rw9VXWPQ5fClRbASom73UjNPUoiQo"} |
