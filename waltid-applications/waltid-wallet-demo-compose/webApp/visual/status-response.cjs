async function readStatusResponse(response, item) {
  try {
    return { projection: await response.json() };
  } catch (error) {
    // Disabling automatic checks and ending a read owner can abort a transfer
    // after headers arrive. Preserve cancellation without inventing a projection.
    const failure = response.request().failure();
    const background = item.method === 'GET' && item.path.endsWith('/credential-status') ||
      item.method === 'POST' && item.path.endsWith('/credential-status/refresh') && item.mode === 'IF_DUE';
    if (background && failure?.errorText === 'net::ERR_ABORTED') return { cancellation: failure };
    throw error;
  }
}

module.exports = { readStatusResponse };
