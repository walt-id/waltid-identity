import { useUserStore } from "@waltid-web-wallet/stores/user.ts";
import { storeToRefs } from "pinia";

interface TokenData {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
}

interface RefreshTokenResponse {
  id: string;
  token: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
}

export class TokenManager {
  private refreshPromise: Promise<string> | null = null;
  private idleTimeout: NodeJS.Timeout | null = null;
  private lastActivity: number = Date.now();
  private readonly IDLE_TIMEOUT_MINUTES = 30; // 30 minutes idle timeout

  constructor() {
    this.setupIdleDetection();
  }

  /**
   * Store tokens after successful login
   */
  storeTokens(tokenData: TokenData) {
    localStorage.setItem('accessToken', tokenData.accessToken);
    localStorage.setItem('refreshToken', tokenData.refreshToken);
    localStorage.setItem('tokenExpiresAt', (Date.now() + tokenData.expiresIn * 1000).toString());
    this.updateLastActivity();
  }

  /**
   * Get current access token, refreshing if necessary
   */
  async getAccessToken(): Promise<string | null> {
    const accessToken = localStorage.getItem('accessToken');
    const refreshToken = localStorage.getItem('refreshToken');
    const expiresAt = localStorage.getItem('tokenExpiresAt');

    if (!accessToken || !refreshToken || !expiresAt) {
      return null;
    }

    // Check if token is expired or will expire in the next 5 minutes
    const now = Date.now();
    const expirationTime = parseInt(expiresAt);
    const fiveMinutesFromNow = now + (5 * 60 * 1000);

    if (expirationTime <= fiveMinutesFromNow) {
      return await this.refreshAccessToken();
    }

    this.updateLastActivity();
    return accessToken;
  }

  /**
   * Refresh the access token using the refresh token
   */
  private async refreshAccessToken(): Promise<string | null> {
    // Prevent multiple simultaneous refresh attempts
    if (this.refreshPromise) {
      return await this.refreshPromise;
    }

    this.refreshPromise = this.performTokenRefresh();
    
    try {
      const result = await this.refreshPromise;
      return result;
    } finally {
      this.refreshPromise = null;
    }
  }

  private async performTokenRefresh(): Promise<string | null> {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) {
      this.clearTokens();
      return null;
    }

    try {
      const response = await fetch('/wallet-api/auth/refresh', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ refreshToken }),
      });

      if (!response.ok) {
        throw new Error('Token refresh failed');
      }

      const data: RefreshTokenResponse = await response.json();
      
      // Store new tokens
      this.storeTokens({
        accessToken: data.token,
        refreshToken: data.refreshToken,
        expiresIn: data.expiresIn,
        tokenType: data.tokenType,
      });

      return data.token;
    } catch (error) {
      console.error('Token refresh failed:', error);
      this.clearTokens();
      // Redirect to login
      await navigateTo('/login');
      return null;
    }
  }

  /**
   * Clear all stored tokens
   */
  clearTokens() {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('tokenExpiresAt');
    this.clearIdleTimeout();
  }

  /**
   * Check if user is currently authenticated
   */
  isAuthenticated(): boolean {
    const accessToken = localStorage.getItem('accessToken');
    const refreshToken = localStorage.getItem('refreshToken');
    return !!(accessToken && refreshToken);
  }

  /**
   * Setup idle detection to automatically logout inactive users
   */
  private setupIdleDetection() {
    // Reset idle timer on user activity
    const events = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click'];
    
    const resetIdleTimer = () => {
      this.updateLastActivity();
      this.clearIdleTimeout();
      this.setIdleTimeout();
    };

    events.forEach(event => {
      document.addEventListener(event, resetIdleTimer, true);
    });

    // Set initial idle timeout
    this.setIdleTimeout();
  }

  private updateLastActivity() {
    this.lastActivity = Date.now();
  }

  private setIdleTimeout() {
    this.clearIdleTimeout();
    this.idleTimeout = setTimeout(() => {
      this.handleIdleTimeout();
    }, this.IDLE_TIMEOUT_MINUTES * 60 * 1000);
  }

  private clearIdleTimeout() {
    if (this.idleTimeout) {
      clearTimeout(this.idleTimeout);
      this.idleTimeout = null;
    }
  }

  private async handleIdleTimeout() {
    console.log('User has been idle for too long, logging out...');
    this.clearTokens();
    
    // Clear user store
    const userStore = useUserStore();
    const { user } = storeToRefs(userStore);
    user.value = {};
    
    // Redirect to login
    await navigateTo('/login');
  }

  /**
   * Get authorization header for API requests
   */
  async getAuthHeader(): Promise<string | null> {
    const token = await this.getAccessToken();
    return token ? `Bearer ${token}` : null;
  }

  /**
   * Cleanup resources
   */
  destroy() {
    this.clearIdleTimeout();
    this.clearTokens();
  }
}

// Global token manager instance
export const tokenManager = new TokenManager();

