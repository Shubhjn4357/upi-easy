import React from 'react';
import type { ToastState } from '../hooks/useToast';
import { IconCheckCircle, IconX } from './ui/icons';

export interface ToastProps {
  toast: ToastState | null;
  onClose: () => void;
}

export function Toast({ toast, onClose }: ToastProps) {
  if (!toast) return null;

  const isError = toast.type === 'error';
  const isInfo = toast.type === 'info';

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed bottom-20 md:bottom-6 right-4 sm:right-6 z-50 flex items-center gap-3 px-4 py-3 rounded-2xl border border-border/80 bg-card/95 backdrop-blur-xl shadow-2xl animate-modal-in text-xs font-semibold text-card-foreground max-w-sm sm:max-w-md transition-all duration-200">
      {/* Icon */}
      <div className="shrink-0 flex items-center justify-center">
        {isError ? (
          <div className="w-6 h-6 rounded-full bg-rose-500/15 text-rose-500 flex items-center justify-center">
            <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
          </div>
        ) : isInfo ? (
          <div className="w-6 h-6 rounded-full bg-blue-500/15 text-blue-500 flex items-center justify-center">
            <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="16" x2="12" y2="12" />
              <line x1="12" y1="8" x2="12.01" y2="8" />
            </svg>
          </div>
        ) : (
          <div className="w-6 h-6 rounded-full bg-emerald-500/15 text-emerald-500 flex items-center justify-center">
            <IconCheckCircle className="w-3.5 h-3.5" />
          </div>
        )}
      </div>

      {/* Message */}
      <span className="leading-snug flex-1 truncate">{toast.message}</span>

      {/* Close button */}
      <button
        type="button"
        onClick={onClose}
        className="text-muted-foreground hover:text-foreground p-1 rounded-lg hover:bg-muted/60 transition-colors shrink-0 cursor-pointer"
        aria-label="Dismiss notification">
        <IconX className="w-3.5 h-3.5" />
      </button>
    </div>
  );
}

export default Toast;
