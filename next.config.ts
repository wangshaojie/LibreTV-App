import type { NextConfig } from 'next';
import fs from 'node:fs';
import path from 'node:path';

/**
 * 版本号以 package.json 为单一来源，构建时注入 process.env.APP_VERSION（/api/status 使用）。
 *
 * 桌面封装有自己的一套版本号（desktop/package.json 里的 2.19.2-desktop.N），
 * 构建时通过 APP_VERSION 环境变量覆盖，否则页脚会显示上游的 2.19.2。
 */
function readAppVersion(): string {
  const override = process.env.APP_VERSION?.trim();
  if (override) return override;
  try {
    const pkg = JSON.parse(fs.readFileSync(path.join(process.cwd(), 'package.json'), 'utf8')) as {
      version?: string;
    };
    return pkg.version || '0.0.0';
  } catch {
    return '0.0.0';
  }
}

const nextConfig: NextConfig = {
  // standalone 输出仅供 Docker 镜像构建使用（DOCKER_BUILD=1）；
  // 本地 next start 在 standalone 模式下不受支持，故按环境切换
  output: process.env.DOCKER_BUILD === '1' ? 'standalone' : undefined,
  reactStrictMode: true,
  env: { APP_VERSION: readAppVersion() },
  // 采集站/豆瓣等上游地址在运行时由用户配置，构建期无法枚举，关闭图片优化改用 <img>
  images: { unoptimized: true },
};

export default nextConfig;
