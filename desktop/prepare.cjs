const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const result = spawnSync(process.execPath, [path.join(root, 'node_modules/next/dist/bin/next'), 'build'], {
  cwd: root,
  stdio: 'inherit',
  env: { ...process.env, DOCKER_BUILD: '1', NEXT_TELEMETRY_DISABLED: '1' },
});
if (result.status !== 0) process.exit(result.status || 1);
const resources = path.join(__dirname, 'resources');
// This directory contains generated build output only.
fs.rmSync(path.join(resources, 'web'), { recursive: true, force: true });
fs.mkdirSync(resources, { recursive: true });
fs.cpSync(path.join(root, '.next/standalone'), path.join(resources, 'web'), { recursive: true });
fs.cpSync(path.join(root, '.next/static'), path.join(resources, 'web/.next/static'), { recursive: true });
fs.cpSync(path.join(root, 'public'), path.join(resources, 'web/public'), { recursive: true });
fs.copyFileSync(path.join(root, 'LICENSE'), path.join(__dirname, 'LICENSE'));
fs.copyFileSync(path.join(root, 'LICENSE'), path.join(resources, 'LICENSE-LibreTV.txt'));
fs.copyFileSync(path.join(__dirname, 'server-bootstrap.cjs'), path.join(resources, 'server-bootstrap.cjs'));
fs.copyFileSync(path.join(__dirname, 'network-fetch.cjs'), path.join(resources, 'network-fetch.cjs'));
require('./source.cjs');
console.log('Standalone web application prepared.');
