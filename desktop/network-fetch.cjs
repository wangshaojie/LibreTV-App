// Run Next's upstream requests through Chromium so they honor Windows system
// proxies/PAC, just as the desktop window does. Node's fetch ignores them.
// This bootstrap is desktop-only; upstream SSRF and redirect checks remain in place.
const { Readable } = require('node:stream');
const { net } = require('electron');
const activeRequests = new Set();

function responseHeaders(values) {
  const headers = new Headers();
  for (const [name, value] of Object.entries(values)) {
    for (const item of Array.isArray(value) ? value : [value]) headers.append(name, item);
  }
  return headers;
}

async function systemFetch(input, init = {}) {
  const redirect = init.redirect || (input instanceof Request ? input.redirect : 'follow');
  if (redirect !== 'manual') return net.fetch(input, { ...init, credentials: 'omit' });

  // net.fetch rejects manual redirects. Expose each 3xx to the existing Next
  // helper instead, so it can validate the next target before issuing it.
  const request = new Request(input, { ...init, duplex: 'half' });
  const signal = init.signal || (input instanceof Request ? input.signal : request.signal);
  signal.throwIfAborted();
  return new Promise((resolve, reject) => {
    const client = net.request({
      url: request.url, method: request.method, redirect: 'manual',
      credentials: 'omit', headers: Object.fromEntries(request.headers),
      cache: request.cache,
    });
    activeRequests.add(client);
    let incoming;
    const abort = () => {
      const reason = signal.reason || new DOMException('Request aborted', 'AbortError');
      reject(reason);
      incoming?.destroy(reason);
      client.abort();
    };
    const cleanup = () => {
      signal.removeEventListener('abort', abort);
      activeRequests.delete(client);
    };
    signal.addEventListener('abort', abort, { once: true });
    // ClientRequest is also a Writable: its close event can precede the HTTP
    // response. Keep cancellation registered until the response actually ends.
    client.on('error', error => {
      cleanup();
      reject(error);
    });
    client.on('abort', () => {
      cleanup();
      reject(signal.reason || new DOMException('Request aborted', 'AbortError'));
    });
    client.on('redirect', (status, _method, location, values) => {
      const headers = responseHeaders(values);
      headers.set('location', location);
      cleanup();
      resolve(new Response(null, { status, headers }));
      client.abort();
    });
    client.once('response', response => {
      incoming = response;
      const noBody = request.method === 'HEAD' || [204, 205, 304].includes(response.statusCode);
      const body = noBody ? null : Readable.toWeb(response, {
        strategy: { highWaterMark: 64 * 1024, size: chunk => chunk.byteLength },
      });
      const result = new Response(body, {
        status: response.statusCode,
        statusText: response.statusMessage,
        headers: responseHeaders(response.headers),
      });
      Object.defineProperty(result, 'url', { value: request.url });
      response.once('end', cleanup);
      response.once('close', () => { cleanup(); client.abort(); });
      resolve(result);
      if (noBody) { cleanup(); client.abort(); }
    });
    if (request.body) request.arrayBuffer().then(body => client.end(Buffer.from(body))).catch(error => {
      client.abort();
      reject(error);
    });
    else client.end();
  });
}

module.exports = { systemFetch };
