const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const skip = new Set(['.git', 'node_modules', '.next', 'resources', 'dist', 'LibreTV-Data', '.build-cache', 'test-release']);
const files = [];
function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (skip.has(entry.name) || (entry.name.startsWith('.env') && entry.name !== '.env.example') || /\.(log|tsbuildinfo)$/.test(entry.name)) continue;
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(fullPath);
    else files.push(path.relative(root, fullPath).replaceAll('\\', '/'));
  }
}
walk(root);
const resources = path.join(__dirname, 'resources');
fs.mkdirSync(resources, { recursive: true });
const list = path.join(resources, 'source-files.txt');
fs.writeFileSync(list, files.join('\n') + '\n');
const result = spawnSync('tar.exe', ['-a', '-cf', path.join(resources, 'LibreTV-Portable-Source.zip'), '-C', root, '-T', list], { stdio: 'inherit' });
fs.unlinkSync(list);
if (result.status !== 0) throw new Error('Failed to create corresponding source archive.');
