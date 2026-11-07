'use client';

import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { cn } from '@/lib/utils';

// —— Toast —— 替代旧版 3 秒/条串行队列，支持并行堆叠与自动过期；
// 支持可选操作按钮（如「撤销」），用于轻量破坏性操作的即时回退。

export type ToastType = 'error' | 'success' | 'info' | 'warning';

export interface ToastAction {
  label: string;
  onClick: () => void;
}

export interface ToastOptions {
  /** 操作按钮，例如删除后的「撤销」；点击后该条提示立即消失 */
  action?: ToastAction;
  /** 展示时长（ms）；带 action 的操作默认 6000，普通提示默认 3000 */
  duration?: number;
}

interface ToastItem {
  id: number;
  message: string;
  type: ToastType;
  action?: ToastAction;
}

interface ToastContextValue {
  toast: (message: string, type?: ToastType, options?: ToastOptions) => void;
}

const ToastContext = createContext<ToastContextValue>({ toast: () => {} });

export function useToast(): ToastContextValue {
  return useContext(ToastContext);
}

// 用实心底色（-solid，深一档）：保证白字对比度达标（普通语义色配白字仅 2-3:1）
const TYPE_STYLES: Record<ToastType, string> = {
  error: 'bg-danger-solid',
  success: 'bg-success-solid',
  info: 'bg-info-solid',
  warning: 'bg-warning-solid',
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const idRef = useRef(0);
  const timersRef = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  const dismiss = useCallback((id: number) => {
    const timer = timersRef.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timersRef.current.delete(id);
    }
    setItems((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const toast = useCallback(
    (message: string, type: ToastType = 'info', options?: ToastOptions) => {
      const id = ++idRef.current;
      const duration = options?.duration ?? (options?.action ? 6000 : 3000);
      setItems((prev) => [...prev.slice(-3), { id, message, type, action: options?.action }]);
      timersRef.current.set(
        id,
        setTimeout(() => {
          timersRef.current.delete(id);
          setItems((prev) => prev.filter((t) => t.id !== id));
        }, duration)
      );
    },
    []
  );

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <div className="fixed top-4 left-1/2 -translate-x-1/2 z-[100] flex flex-col gap-2 items-center pointer-events-none">
        {items.map((t) => (
          <div
            key={t.id}
            // 读屏可见：错误用 alert（立即朗读），其余用 status（礼貌播报）
            role={t.type === 'error' ? 'alert' : 'status'}
            className={cn(
              'px-4 py-2.5 text-white text-sm max-w-md animate-slide-up',
              'flex items-center gap-3 pointer-events-auto',
              TYPE_STYLES[t.type]
            )}
          >
            <span>{t.message}</span>
            {t.action && (
              <button
                className="shrink-0 font-medium underline underline-offset-2 hover:opacity-80 transition-opacity"
                onClick={() => {
                  dismiss(t.id);
                  t.action?.onClick();
                }}
              >
                {t.action.label}
              </button>
            )}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
