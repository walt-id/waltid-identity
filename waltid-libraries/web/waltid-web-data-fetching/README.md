# waltid-web-data-fetching

## Features

- Multiplatform, Coroutine-based
- Fetch data over HTTP
- Provide HTTP client
- Automatic body decoding
- Graceful error handling (timeouts, 404, remote server errors, etc.)
- Content encoding

### Allow/disallow (user-provided) URLs

- Configure allowed/disallowed protocols
- Configure allowed/disallowed ports
- Configure allowed/disallowed hosts
- Configure allowed/disallowed URLs

### Configurable caching

- Configure maximum cache size
- LRU / Expire after access
- FIFO / Expire after write

### Configurable retry-logic

- Retry on server error / exceptions
- Constant delay / exponential backoff
- Add retry headers

### JSON decoding
- Configurable options to:
  - allow unknown fields
  - allow comments
  - allow trailing commas
  - case-insensitive enum parsing
  - lenient parsing (unquoted key and string literals)
- Handle JSON parsing also when server responds with default instead of explicit content type

### Configurable timeouts

- Configure connect/request/socket timeouts

### Custom request

- Fail on error / proceed on error
- Basic Auth/Bearer Auth, or custom Headers & Cookies
- Change method
- User agent (impersonate browser, impersonate curl, custom)

### Engine

- Enable / disable follow redirects
- Configurable proxy (HTTP or SOCKS)
- Set HTTP pipelining advice

