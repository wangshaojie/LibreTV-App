// Real Electron utility-process tests against a controlled local HTTP fixture.
const { app, utilityProcess } = require('electron');
const fs = require('node:fs');
const path = require('node:path');

if (process.type !== 'utility') {
  app.whenReady().then(() => {
    const worker = utilityProcess.fork(__filename, [], { partition: 'network-integration', stdio: 'pipe' });
    const timer = setTimeout(() => { worker.kill(); app.exit(1); }, 15000);
    worker.stderr.on('data', data => process.stderr.write(data));
    worker.stdout.on('data', data => process.stdout.write(data));
    worker.once('message', result => {
      clearTimeout(timer);
      fs.writeFileSync(path.join(__dirname, '.build-cache/network-result.json'), JSON.stringify(result, null, 2));
      worker.kill();
      app.exit(result.success ? 0 : 1);
    });
  });
} else {
  (async () => {
    const assert = require('node:assert/strict');
    const http = require('node:http');
    const { once } = require('node:events');
    const { systemFetch } = require('./network-fetch.cjs');
    const results = [];
    const progress = text => {
      fs.writeFileSync(path.join(__dirname, '.build-cache/network-progress.txt'), text);
      console.log(text);
    };
    let redirectedTargetRequests = 0;
    let streamClosed;
    const closeNotice = new Promise(resolve => { streamClosed = resolve; });
    const fixture = http.createServer((req, res) => {
      if (req.url === '/redirect') { res.writeHead(302, { Location: '/target' }); res.end(); }
      else if (req.url === '/target') { redirectedTargetRequests++; res.end('target'); }
      else if (req.url === '/headers') { res.writeHead(206, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(req.headers)); }
      else if (req.url === '/slow') { /* wait for the client to abort */ }
      else if (req.url === '/stream') {
        res.writeHead(200, { 'Content-Type': 'video/mp2t' });
        res.write(Buffer.alloc(1024, 7));
        const timer = setInterval(() => res.write(Buffer.alloc(1024, 7)), 10);
        res.on('close', () => { clearInterval(timer); streamClosed(); });
      } else if (req.url === '/empty') { res.writeHead(204); res.end(); }
      else if (req.url === '/post') { let body = ''; req.on('data', chunk => { body += chunk; }); req.on('end', () => res.end(body)); }
      else { res.writeHead(404); res.end(); }
    });
    fixture.listen(0, '127.0.0.1');
    await once(fixture, 'listening');
    const base = 'http://127.0.0.1:' + fixture.address().port;
    try {
      progress('manual redirect');
      const redirect = await systemFetch(base + '/redirect', { redirect: 'manual' });
      assert.equal(redirect.status, 302);
      assert.equal(new URL(redirect.headers.get('location'), base).pathname, '/target');
      assert.equal(redirectedTargetRequests, 0);
      results.push('Manual redirect returns Location without following it');

      progress('follow redirect');
      const follow = await systemFetch(base + '/redirect');
      assert.equal(await follow.text(), 'target');
      results.push('Normal fetch follows redirects');

      progress('range headers');
      const ranged = await systemFetch(base + '/headers', { redirect: 'manual', headers: { Range: 'bytes=0-1', Referer: base + '/' } });
      assert.equal(ranged.status, 206);
      const headers = await ranged.json();
      assert.equal(headers.range, 'bytes=0-1');
      assert.equal(headers.referer, base + '/');
      results.push('Range, Referer and partial-response status preserved');

      progress('timeout');
      const started = Date.now();
      const deadline = AbortSignal.timeout(100);
      deadline.addEventListener('abort', () => progress('timeout emitted'));
      const watchdog = setTimeout(() => progress('timeout state: ' + deadline.aborted), 300);
      await assert.rejects(systemFetch(base + '/slow', { redirect: 'manual', signal: deadline }), error => error.name === 'TimeoutError');
      clearTimeout(watchdog);
      assert.ok(Date.now() - started < 2000);
      results.push('Timeout cancels an upstream request');

      progress('stream');
      const stream = await systemFetch(base + '/stream', { redirect: 'manual' });
      const reader = stream.body.getReader();
      assert.ok((await reader.read()).value.byteLength > 0);
      progress('stream cancel');
      await reader.cancel();
      await Promise.race([closeNotice, new Promise((_, reject) => setTimeout(() => reject(new Error('Stream not cancelled')), 1000))]);
      results.push('Streaming starts before EOF; cancellation closes upstream');

      progress('empty and post');
      const empty = await systemFetch(base + '/empty', { redirect: 'manual' });
      assert.equal(empty.status, 204);
      assert.equal(await empty.text(), '');
      const post = await systemFetch(base + '/post', { redirect: 'manual', method: 'POST', body: 'hello' });
      assert.equal(await post.text(), 'hello');
      results.push('No-content responses and POST request bodies supported');
      process.parentPort.postMessage({ success: true, results });
    } finally {
      fixture.closeAllConnections();
      fixture.close();
    }
  })().catch(error => process.parentPort.postMessage({ success: false, error: error.stack }));
}
