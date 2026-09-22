import React from 'react';
import type { ToastState } from '../hooks/useToast';

export interface ToastProps {
  toast: ToastState | null;
  onClose: () => void;
}

export function Toast({ toast, onClose }: ToastProps) {
  if (!toast) return null;

  return (
    <div className="fixed bottom-20 md:bottom-6 right-6 z-50 flex items-center gap-3 px-4 py-3 rounded-xl border border-border bg-card shadow-2xl animate-fade-in text-xs font-semibold text-card-foreground">
      <div
        className={`w-2 h-2 rounded-full ${
          toast.type === 'error'
            ? 'bg-destructive'
            : toast.type === 'info'
            ? 'bg-blue-500'
            : 'bg-emerald-500'
        }`}
      />
      <span>{toast.message}</span>
      <button
        onClick={onClose}
        className="ml-2 text-muted-foreground hover:text-foreground">
        ✕
      </button>
    </div>
  );
}

export default Toast;
