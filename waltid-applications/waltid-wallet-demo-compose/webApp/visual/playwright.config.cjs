const { defineConfig } = require('@playwright/test');
const { execFileSync } = require('node:child_process');
const { readdirSync } = require('node:fs');
const path = require('node:path');
const catalogue = require('./catalogue.json');

const record = process.env.WALLET_VISUAL_RECORD === '1';
const ci = ['CI', 'GITHUB_ACTIONS', 'GITLAB_CI', 'BUILD_BUILDID', 'JENKINS_URL', 'TEAMCITY_VERSION']
  .some(key => process.env[key] !== undefined);
if ((record && ci) || (process.argv.some(arg => arg.startsWith('--update-snapshots') || arg.startsWith('-u')) && (!record || ci))) {
  throw new Error('Browser references may only be recorded deliberately outside CI.');
}
if (process.platform !== 'darwin' || process.arch !== 'arm64' ||
    !execFileSync('sw_vers', ['-productVersion'], { encoding: 'utf8' }).startsWith('27.')) {
  throw new Error('These browser references require macOS 27 arm64. Review other platform baselines separately.');
}
const snapshots = path.join(__dirname, 'snapshots');
if (!record) {
  const actual = readdirSync(snapshots).filter(name => name.endsWith('.png')).sort();
  const expected = catalogue.states.map(state => `${state.id.replaceAll('.', '-')}.png`).sort();
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error('Browser catalogue and references differ.');
}
const output = path.resolve(__dirname, '../build/reports/browser-visual');
module.exports = defineConfig({
  testDir: __dirname,
  testMatch: 'account.spec.cjs',
  workers: 1,
  retries: 0,
  timeout: 30000,
  updateSnapshots: record ? 'all' : 'none',
  snapshotPathTemplate: `${snapshots}/{arg}{ext}`,
  outputDir: path.join(output, 'results'),
  reporter: [['list'], ['html', { outputFolder: path.join(output, 'html'), open: 'never' }],
    ['json', { outputFile: path.join(output, 'results.json') }]],
  metadata: {
    sourceSha: execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim(),
    dirty: !!execFileSync('git', ['status', '--porcelain'], { encoding: 'utf8' }).trim(),
    environment: catalogue.environment,
    requirements: catalogue.requirements,
    mode: record ? 'record' : 'compare',
  },
  expect: { timeout: 10000, toHaveScreenshot: { threshold: 0, maxDiffPixels: 0 } },
  use: {
    baseURL: 'http://127.0.0.1:8073',
    locale: 'en-US', timezoneId: 'UTC', colorScheme: 'light', deviceScaleFactor: 1,
    trace: 'retain-on-failure', screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'python3 -m http.server 8073 --bind 127.0.0.1 --directory ../build/dist/wasmJs/productionExecutable',
    url: 'http://127.0.0.1:8073', reuseExistingServer: false,
  },
});
