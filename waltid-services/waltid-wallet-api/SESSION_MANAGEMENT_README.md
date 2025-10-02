# Session Management and Token Security Improvements

This document describes the improvements made to the Walt.Id Wallet service's session management and token handling to address security concerns raised during penetration testing.

## Issues Addressed

1. **Logout doesn't invalidate tokens** - Tokens remained valid even after logout
2. **Token expiration only in days** - No support for minutes/seconds granularity
3. **No refresh token mechanism** - No way to refresh tokens without re-authentication
4. **No idle timeout** - No automatic logout for inactive users

## Solutions Implemented

### 1. Token Blacklisting Service

**File**: `src/main/kotlin/id/walt/webwallet/service/TokenBlacklistService.kt`

- Implements server-side token blacklisting
- Tokens are invalidated immediately upon logout
- Automatic cleanup of expired tokens
- Thread-safe implementation using coroutines

### 2. Flexible Token Expiration

**File**: `src/main/kotlin/id/walt/webwallet/utils/TimeUtils.kt`

- Support for various time formats:
  - `"15m"` or `"15 minutes"` → 15 minutes
  - `"2h"` or `"2 hours"` → 2 hours  
  - `"1d"` or `"1 days"` → 1 day
  - `"30s"` or `"30 seconds"` → 30 seconds
  - `"3600"` → 3600 seconds (numeric fallback)

### 3. Refresh Token Mechanism

**Files**: 
- `src/main/kotlin/id/walt/webwallet/web/controllers/auth/RefreshTokenController.kt`
- Updated `AuthController.kt`

- Access tokens with short expiration (default: 15 minutes)
- Refresh tokens with longer expiration (default: 7 days)
- Automatic token rotation for security
- Seamless token refresh without user intervention

### 4. Idle Timeout Implementation

**File**: `apps/waltid-dev-wallet/src/composables/tokenManager.ts`

- Automatic logout after configurable idle time (default: 30 minutes)
- Activity detection (mouse, keyboard, touch events)
- Graceful token refresh before timeout

### 5. Enhanced Frontend Token Management

**Files**:
- `apps/waltid-dev-wallet/src/composables/tokenManager.ts`
- `apps/waltid-dev-wallet/src/composables/apiClient.ts`
- `apps/waltid-dev-wallet/src/composables/authentication.ts`

- Automatic token refresh
- Idle timeout handling
- Secure token storage
- API client with automatic authentication

## Configuration

### Backend Configuration

Update `config/auth.conf`:

```hocon
# Access token lifetime (15 minutes)
tokenLifetime = "15m"

# Refresh token lifetime (7 days)  
refreshTokenLifetime = "7d"

# Idle timeout in minutes (30 minutes)
idleTimeoutMinutes = 30
```

### Frontend Configuration

The frontend automatically uses the backend configuration. The idle timeout can be customized in `tokenManager.ts`:

```typescript
private readonly IDLE_TIMEOUT_MINUTES = 30; // 30 minutes idle timeout
```

## API Endpoints

### New Endpoints

- `POST /wallet-api/auth/refresh` - Refresh access token using refresh token

### Updated Endpoints

- `POST /wallet-api/auth/login` - Now returns refresh token and expiration info
- `POST /wallet-api/auth/logout` - Now properly invalidates tokens

## Security Features

### Token Security
- **Short-lived access tokens** (15 minutes default)
- **Refresh token rotation** - New refresh token issued on each refresh
- **Server-side token blacklisting** - Immediate invalidation on logout
- **Automatic cleanup** - Expired tokens removed from blacklist

### Session Management
- **Idle timeout** - Automatic logout after inactivity
- **Activity detection** - Resets timeout on user interaction
- **Secure storage** - Refresh tokens stored in httpOnly cookies
- **Token validation** - All tokens validated against blacklist

## Migration Guide

### For Existing Deployments

1. **Update configuration** - Add new token settings to `auth.conf`
2. **Deploy backend** - New token handling is backward compatible
3. **Update frontend** - Deploy new token management system
4. **Test logout** - Verify tokens are properly invalidated

### Configuration Examples

**Development Environment**:
```hocon
tokenLifetime = "5m"          # 5 minutes
refreshTokenLifetime = "1d"   # 1 day
idleTimeoutMinutes = 15       # 15 minutes
```

**Production Environment**:
```hocon
tokenLifetime = "15m"         # 15 minutes
refreshTokenLifetime = "7d"   # 7 days
idleTimeoutMinutes = 30       # 30 minutes
```

**High Security Environment**:
```hocon
tokenLifetime = "5m"          # 5 minutes
refreshTokenLifetime = "1d"   # 1 day
idleTimeoutMinutes = 10       # 10 minutes
```

## Monitoring

### Token Cleanup Service

The `TokenCleanupService` runs automatically and:
- Cleans up expired tokens every hour
- Logs blacklist size for monitoring
- Handles cleanup errors gracefully

### Logging

Key events are logged:
- Token blacklisting on logout
- Token refresh attempts
- Idle timeout triggers
- Cleanup service operations

## Testing

### Manual Testing

1. **Login** - Verify tokens are issued with correct expiration
2. **Token Refresh** - Test automatic refresh before expiration
3. **Logout** - Verify tokens are blacklisted and invalidated
4. **Idle Timeout** - Test automatic logout after inactivity

### Automated Testing

The system includes comprehensive error handling and can be tested with:
- Token expiration scenarios
- Invalid refresh token handling
- Network failure recovery
- Concurrent refresh attempts

## Performance Considerations

- **Memory Usage** - Token blacklist uses in-memory storage (consider Redis for production)
- **Cleanup Frequency** - Runs every hour (configurable)
- **Token Validation** - O(1) lookup time for blacklist checks
- **Frontend Efficiency** - Minimal API calls with smart refresh logic

## Future Enhancements

1. **Redis Integration** - Replace in-memory blacklist with Redis
2. **Token Analytics** - Track token usage patterns
3. **Advanced Policies** - Per-user token policies
4. **Audit Logging** - Comprehensive security event logging

## Troubleshooting

### Common Issues

1. **Tokens not refreshing** - Check refresh token validity and expiration
2. **Premature logout** - Verify idle timeout configuration
3. **Memory usage** - Monitor token blacklist size
4. **CORS issues** - Ensure proper CORS configuration for token endpoints

### Debug Mode

Enable debug logging to troubleshoot token issues:
```kotlin
logger.debug { "Token operations" }
```

## Security Recommendations

1. **Use HTTPS** - Always use secure connections in production
2. **Regular Rotation** - Rotate signing keys regularly
3. **Monitor Usage** - Track token refresh patterns for anomalies
4. **Secure Storage** - Ensure refresh tokens are stored securely
5. **Audit Logs** - Monitor authentication events for security issues

