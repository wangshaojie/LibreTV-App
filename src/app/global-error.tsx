'use client';

/**
 * 根布局级错误兜底：layout 本身崩溃时启用，必须自带 <html>/<body>。
 * 此时主题脚本等均已失效，用内联样式保证基本可读。
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <html lang="zh-CN">
      <body
        style={{
          margin: 0,
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 12,
          background: '#0e0e10',
          color: '#f5f5f7',
          fontFamily: 'system-ui, sans-serif',
          textAlign: 'center',
          padding: 16,
        }}
      >
        <p style={{ margin: 0, fontSize: 14 }}>应用发生严重错误，请刷新页面重试。</p>
        <button
          onClick={reset}
          style={{
            padding: '6px 16px',
            borderRadius: 8,
            border: '1px solid #2a2a2f',
            background: '#202024',
            color: '#f5f5f7',
            cursor: 'pointer',
          }}
        >
          刷新
        </button>
        {/* digest 用于线上日志定位，不含敏感信息，展示无妨 */}
        {error.digest && (
          <p style={{ margin: 0, fontSize: 11, color: '#71717a' }}>错误编号：{error.digest}</p>
        )}
      </body>
    </html>
  );
}
