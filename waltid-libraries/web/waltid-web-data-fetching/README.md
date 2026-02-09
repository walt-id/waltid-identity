# waltid-web-data-fetching

## Usage

1. Create a WebDataFetcher for the use case (e.g. did:web resolving, JSON Schema retrieval, OpenID
   metadata viewing, etc):
   ```kotlin
   private val schemaFetcher = WebDataFetcher<JsonObject>("schema-policy")
   ```
2. Make sure the configuration is loaded at runtime (applied with WebDataFetcherManager).
3. Retrieve the desired data:
   ```kotlin
   val response: JsonObject = schemaFetcher.fetch<JsonObject>(schemaUrl)
   ```

*Hint:* Set the response format with the template (`<Response type>`).

## Features

- Multiplatform, Coroutine-based
- Fetch data over HTTP
- Provide HTTP client
- Automatic body decoding
- Graceful error handling (timeouts, 404, remote server errors, etc.)
- Content encoding

### Allow/disallow (user-provided) URLs

- Configure allowed/disallowed protocols: `"protocols": { "allowHttp": true, "allowHttps": true }`
- Configure allowed/disallowed ports: `"ports": { "whitelist": [ 80, 443 ], "blacklist": [ 25 ] }`
- Configure allowed/disallowed hosts:
  `"hosts": { "whitelist": [ "localhost", "127.0.0.1" ], "blacklist": [ "google.com" ] }`
- Configure allowed/disallowed URLs:
  `"urls": { "whitelist": [ "https://example.org/allowed" ], "blacklist": [ "https://example.org/disallowed" ] }`

### Configurable caching

- Configure maximum cache size: `"maximumCacheSize": 500`
- LRU / Expire after access: `"expireAfterAccess": "PT15M"`
- FIFO / Expire after write: `"expireAfterWrite": "PT10M"`

### Configurable retry-logic

- Retry on server error / exceptions: `"retryOn": "RETRY_ON_EXCEPTION_OR_SERVER_ERRORS"`
    - Available options: `NO_RETRY`, `RETRY_ON_SERVER_ERRORS`, `RETRY_ON_EXCEPTION`,
      `RETRY_ON_EXCEPTION_OR_SERVER_ERRORS`
- Constant delay / exponential backoff
    - Exponential:
      `{ "type": "exponential", "base": 2.0, "baseDelay": "PT1S", "maxDelay": "PT6S", "randomization": "PT1S", "respectRetryAfterHeader": true }`
    - Constant:
      `{ "type": "constant", "delay": "PT1S", "randomization": "PT1S", "respectRetryAfterHeader": true }`
- Add retry headers: `"addRetryCountHeader": true`
- Max retry count: `"maxRetryCount": 3`

### JSON decoding

- Configurable options to:
    - allow unknown fields: `"allowUnknownFields": true`
    - allow comments: `"ignoreComments": true`
    - allow trailing commas: `"allowTrailingCommas": true,`
    - case-insensitive enum parsing: `"caseInsensitiveEnums": true`
    - lenient parsing (unquoted key and string literals): `"lenientParsing": false`
- Handle JSON parsing also when server responds with default instead of explicit content type

### Configurable timeouts

- Configure connect/request/socket timeouts:
  `"timeouts": { "requestTimeout": "PT30S", "connectTimeout": "PT10S", "socketTimeout": "PT5S" },`

### Custom request

- Fail on error / proceed on error: `"expectSuccess": false`
- Basic Auth/Bearer Auth, or custom Headers & Cookies:
  `"headers": { "X-Requested-By": "walt.id" }, "cookies": { "my_login": "123456" }, "auth": { "type": "bearer", "token": "my-token" },`
    - Auth:
        - Basic: `{"type": "basic", "username": "user", "password": "pass"}`
        - Bearer: `{"type": "bearer", "token": "123"}`
- Change method: `"method": "Get"`
    - Methods: `Get`, `Post`, `Put`, `Patch`, `Delete`, `Head`, `Options`
- User agent (impersonate browser, impersonate curl, custom)
    - Custom: `{"type": "custom"}`
    - Curl: `{"type": "curl"}`
    - Browser: `{"type": "browser"}`

### Engine

- Enable / disable follow redirects: `"followRedirects": false`
- Configurable proxy (HTTP or SOCKS):
    - `{"type": "http", "url": "..."}`
    - `{"type": "socks", "host": "127.0.0.1", "port": 12345}`
- Set HTTP pipelining advice: `"pipelining": false,`

### Logging

- Enable/disable logging: `"enable": true`
- Configure logging format: `"format": "Default",`
- Set log level: `"level": "HEADERS"`
    - `ALL`, `HEADERS`, `BODY`, `INFO`, `NONE`

## Configuration

This is a complete example of all possible options:

```json
{
  "url": {
    "protocols": {
      "allowHttp": true,
      "allowHttps": true
    },
    "ports": {
      "whitelist": [
        80,
        443
      ],
      "blacklist": [
        25
      ]
    },
    "hosts": {
      "whitelist": [
        "localhost",
        "127.0.0.1"
      ],
      "blacklist": [
        "google.com"
      ]
    },
    "urls": {
      "whitelist": [
        "https://example.org/allowed"
      ],
      "blacklist": [
        "https://example.org/disallowed"
      ]
    }
  },
  "cache": {
    "expireAfterAccess": "PT15M",
    "expireAfterWrite": "PT10M",
    "maximumCacheSize": 500
  },
  "timeouts": {
    "requestTimeout": "PT30S",
    "connectTimeout": "PT10S",
    "socketTimeout": "PT5S"
  },
  "retry": {
    "retryOn": "RETRY_ON_EXCEPTION_OR_SERVER_ERRORS",
    "maxRetryCount": 3,
    "delay": {
      "type": "exponential",
      "base": 2.0,
      "baseDelay": "PT1S",
      "maxDelay": "PT6S",
      "randomization": "PT1S",
      "respectRetryAfterHeader": true
    },
    "addRetryCountHeader": true
  },
  "request": {
    "method": "Get",
    "headers": {
      "X-Requested-By": "walt.id"
    },
    "cookies": {
      "my_login": "123456"
    },
    "auth": {
      "type": "bearer",
      "token": "my-token"
    },
    "userAgent": {
      "type": "custom",
      "agent": "custom user agent"
    },
    "expectSuccess": false
  },
  "engine": {
    "followRedirects": false,
    "pipelining": false,
    "proxy": {
      "type": "socks",
      "host": "127.0.0.1",
      "port": 12345
    }
  },
  "decoding": {
    "allowUnknownFields": true,
    "caseInsensitiveEnums": true,
    "lenientParsing": false,
    "coerce": false,
    "explicitNullValues": true,
    "allowTrailingCommas": true,
    "ignoreComments": true
  },
  "logging": {
    "enable": true,
    "format": "Default",
    "level": "HEADERS"
  }
}
```
