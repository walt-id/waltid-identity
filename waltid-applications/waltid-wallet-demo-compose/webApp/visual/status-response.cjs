async function retainResponseBodies(page) {
  // Fetch can close its reader after consuming JSON, invalidating renderer-owned bodies.
  // Keep bounded copies in Chromium without intercepting or replaying requests.
  const session = await page.context().newCDPSession(page);
  await session.send('Network.enable');
  await session.send('Network.configureDurableMessages', { maxTotalBufferSize: 8 * 1024 * 1024, maxResourceBufferSize: 1024 * 1024 });
}

async function readStatusResponse(response, item) {
  try {
    return { projection: await response.json() };
  } catch (error) {
    // Automatic startup and read owners can end after headers arrive.
    // Preserve confirmed cancellation without inventing a projection.
    const failure = response.request().failure();
    const background = item.method === 'GET' && item.path.endsWith('/credential-status') ||
      item.method === 'POST' && item.path.endsWith('/credential-status/refresh') && item.mode === 'IF_DUE' ||
      item.method === 'POST' && item.path.endsWith('/credential-status/context') && item.statusCheckOwner === 'automatic';
    if (background && failure?.errorText === 'net::ERR_ABORTED') return { cancellation: failure };
    throw error;
  }
}

module.exports = { readStatusResponse, retainResponseBodies };
