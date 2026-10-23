const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const resources = path.join(__dirname, 'resources');
fs.mkdirSync(resources, { recursive: true });
// Committed sources exclude ignored profiles, build caches and signing keys.
// git archive also preserves Unicode filenames reliably on Windows.
const result = spawnSync('git', ['archive', '--format=zip', '--output=' + path.join(resources, 'LibreTV-Portable-Source.zip'), 'HEAD'], { cwd: root, stdio: 'inherit' });
if (result.status !== 0) throw new Error('Failed to create corresponding source archive.');
