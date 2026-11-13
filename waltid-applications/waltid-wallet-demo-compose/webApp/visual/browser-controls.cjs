const { expect } = require('@playwright/test');
const { createHash } = require('node:crypto');

// Compose's ARIA mirror supplies bounds, while the canvas receives the actual pointer input.
async function clickControl(page, locator) {
  await expect(locator).toBeVisible();
  const box = await locator.boundingBox();
  expect(box).not.toBeNull();
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
}

async function fillControl(page, locator, value, inputMode) {
  await clickControl(page, locator);
  // ARIA textboxes are contenteditable mirrors. Filling them can race the
  // canvas focus change without delivering text to Compose's backing editor.
  const editor = page.locator('input.compose-backing-field');
  await expect(editor).toHaveAttribute('inputmode', inputMode);
  await expect(editor).toBeFocused();
  await editor.fill(value);
  await expect(editor).toHaveValue(value);
}

async function waitForSettledImage(page, clip) {
  let previous;
  let unchangedSince;
  // CSS animation controls do not stop Compose's canvas animations. Two equal
  // frames can occur before the enabled-state color transition has finished.
  await expect.poll(async () => {
    const pixels = await page.screenshot({ animations: 'disabled', caret: 'hide', ...(clip ? { clip } : {}) });
    const current = createHash('sha256').update(pixels).digest('hex');
    const now = performance.now();
    if (current !== previous) {
      previous = current;
      unchangedSince = now;
    }
    return now - unchangedSince >= 500;
  }, { timeout: 10000, intervals: [100] }).toBe(true);
}

async function submitEditedForm(page, locator) {
  await page.keyboard.press('Tab');
  // Filling the backing editor precedes Compose's rendered form/enabled state.
  // Settle the action without waiting on a blinking canvas caret, then click once.
  const box = await locator.boundingBox();
  expect(box).not.toBeNull();
  await waitForSettledImage(page, box);
  await clickControl(page, locator);
}

async function revealReviewControl(page, locator) {
  await expect(locator).toBeVisible();
  const viewport = page.viewportSize();
  const share = page.getByRole('button', { name: 'Share', exact: true });
  const footer = await share.count() ? await share.boundingBox() : null;
  const bottom = footer ? footer.y - 8 : viewport.height - 12;
  for (let n = 0; n < 8; n++) {
    await waitForSettledImage(page);
    const box = await locator.boundingBox();
    if (box && box.y >= 72 && box.y + box.height <= bottom) return;
    // ARIA visibility does not account for the canvas scroll clip or pinned footer.
    // Wheel inside this sheet's actual column, rather than a fixed page coordinate.
    await page.mouse.move(box ? box.x + box.width / 2 : viewport.width / 2, Math.min(bottom - 32, viewport.height / 2));
    await page.mouse.wheel(0, box && box.y < 72 ? -250 : 250);
    await waitForSettledImage(page);
  }
  throw new Error('The complete control must be inside the review content above the pinned actions');
}


module.exports = { clickControl, fillControl, waitForSettledImage, submitEditedForm, revealReviewControl };
