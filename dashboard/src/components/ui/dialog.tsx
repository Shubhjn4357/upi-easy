// Modern Glassmorphic Dialog components with smooth animations and keyboard accessibility
import React, { useEffect } from 'react';
import { IconX } from './icons';

export interface DialogProps {
  open: boolean;
  onOpenChange?: (open: boolean) => void;
  children: React.ReactNode;
}

export function Dialog({ open, onOpenChange, children }: DialogProps) {
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        onOpenChange?.(false);
      }
    }
    if (open) {
      document.body.style.overflow = 'hidden';
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      document.body.style.overflow = 'unset';
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [open, onOpenChange]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 md:p-6 overflow-y-auto">
      {/* Backdrop with modern blur */}
      <div
        className="fixed inset-0 bg-black/65 backdrop-blur-md transition-opacity animate-fade-in"
        onClick={() => onOpenChange?.(false)}
        aria-hidden="true"
      />
      {/* Content wrapper */}
      <div className="relative z-50 w-full flex items-center justify-center my-auto pointer-events-none">
        <div className="pointer-events-auto w-full flex justify-center">
          {children}
        </div>
      </div>
    </div>
  );
}

export type DialogSize = 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full';

export interface DialogContentProps extends React.HTMLAttributes<HTMLDivElement> {
  size?: DialogSize;
  className?: string;
  children?: React.ReactNode;
  showCloseButton?: boolean;
  onClose?: () => void;
}

export function DialogContent({
  size = 'md',
  className = '',
  children,
  showCloseButton = false,
  onClose,
  ...props
}: DialogContentProps) {
  const sizeClasses: Record<DialogSize, string> = {
    sm: 'max-w-sm',
    md: 'max-w-lg',
    lg: 'max-w-2xl',
    xl: 'max-w-4xl',
    '2xl': 'max-w-5xl',
    full: 'max-w-[95vw] h-[90vh]',
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      className={`glass-modal relative w-full ${sizeClasses[size] || sizeClasses.md} rounded-3xl border border-border/80 bg-card p-6 shadow-2xl text-card-foreground animate-modal-in transition-all duration-200 ${className}`}
      {...props}>
      {showCloseButton && (
        <button
          type="button"
          onClick={onClose}
          className="absolute top-4 right-4 h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-muted/60 transition-all duration-150 active:scale-95 cursor-pointer"
          aria-label="Close dialog">
          <IconX className="w-4 h-4" />
        </button>
      )}
      {children}
    </div>
  );
}

export function DialogHeader({ className = '', children, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`flex flex-col space-y-1.5 text-left pb-4 border-b border-border/60 ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export function DialogTitle({ className = '', children, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3
      className={`text-base sm:text-lg font-bold leading-none tracking-tight text-foreground ${className}`}
      {...props}>
      {children}
    </h3>
  );
}

export function DialogDescription({ className = '', children, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return (
    <p
      className={`text-xs text-muted-foreground mt-1.5 leading-relaxed ${className}`}
      {...props}>
      {children}
    </p>
  );
}

export function DialogFooter({ className = '', children, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2 gap-2 pt-4 border-t border-border/60 mt-4 ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export default Dialog;
