// Desktop-only bootstrap: keep Next.js validation and safe redirect handling.
const path = require('node:path');
globalThis.fetch = require('./network-fetch.cjs').systemFetch;
require(path.join(__dirname, 'web', 'server.js'));
