import { defineConfig } from '@playwright/test';

const port = process.env.PLAYWRIGHT_PORT?.trim() || '3002';
const baseURL =
  process.env.PLAYWRIGHT_BASE_URL?.trim() || `http://127.0.0.1:${port}`;

export default defineConfig({
  testDir: './tests/ui',
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: process.env.CI ? 'github' : 'list',
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL,
    trace: 'retain-on-failure',
  },
  webServer: {
    command: `bun run dev -- -p ${port} -H 127.0.0.1`,
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
