import os from "node:os"
import path from "node:path"

export default {
  outputDir: path.join(os.tmpdir(), "waltid-web-portal-playwright-results"),
  use: {
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
}
