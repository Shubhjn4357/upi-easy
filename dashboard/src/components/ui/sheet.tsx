// Modern Drawer / Sheet component with smooth spring animations, backdrop blur, and multiple sides
import React, { useEffect } from 'react';
import { IconX } from './icons';

export interface SheetProps {
  open: boolean;
  onOpenChange?: (open: boolean) => void;
  children: React.ReactNode;
}

export function Sheet({ open, onOpenChange, children }: SheetProps) {
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
    <div className="fixed inset-0 z-50 overflow-hidden">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/65 backdrop-blur-md transition-opacity animate-fade-in"
        onClick={() => onOpenChange?.(false)}
        aria-hidden="true"
      />
      {children}
    </div>
  );
}

export type SheetSide = 'top' | 'bottom' | 'left' | 'right';

export interface SheetContentProps extends React.HTMLAttributes<HTMLDivElement> {
  side?: SheetSide;
  className?: string;
  children?: React.ReactNode;
  showCloseButton?: boolean;
  onClose?: () => void;
}

export function SheetContent({
  side = 'bottom',
  className = '',
  children,
  showCloseButton = true,
  onClose,
  ...props
}: SheetContentProps) {
  const sideClasses: Record<SheetSide, string> = {
    top: 'inset-x-0 top-0 border-b border-border/80 animate-in slide-in-from-top duration-300',
    bottom:
      'inset-x-0 bottom-0 border-t border-border/80 rounded-t-3xl max-h-[88vh] overflow-y-auto animate-bottom-sheet-up',
    left: 'inset-y-0 left-0 h-full w-full max-w-md border-r border-border/80 animate-drawer-left',
    right:
      'inset-y-0 right-0 h-full w-full max-w-md border-l border-border/80 animate-drawer-right',
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      className={`glass-modal fixed z-50 flex flex-col bg-card/95 p-6 shadow-2xl transition ease-in-out border-border text-card-foreground ${sideClasses[side] || sideClasses.bottom} ${className}`}
      {...props}>
      {/* Mobile drag handle for bottom drawer */}
      {side === 'bottom' && (
        <div className="w-12 h-1.5 rounded-full bg-muted-foreground/30 mx-auto -mt-2 mb-4 shrink-0 transition-opacity hover:opacity-100" />
      )}

      {showCloseButton && (
        <button
          type="button"
          onClick={onClose}
          className="absolute top-4 right-4 h-8 w-8 rounded-full flex items-center justify-center text-muted-foreground hover:text-foreground hover:bg-muted/60 transition-all duration-150 active:scale-95 cursor-pointer z-10"
          aria-label="Close drawer">
          <IconX className="w-4 h-4" />
        </button>
      )}

      {children}
    </div>
  );
}

export function SheetHeader({ className = '', children, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`flex flex-col space-y-1.5 text-left pb-4 border-b border-border/60 ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export function SheetTitle({ className = '', children, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3
      className={`text-base sm:text-lg font-bold leading-none tracking-tight text-foreground ${className}`}
      {...props}>
      {children}
    </h3>
  );
}

export function SheetDescription({ className = '', children, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return (
    <p
      className={`text-xs text-muted-foreground mt-1 leading-relaxed ${className}`}
      {...props}>
      {children}
    </p>
  );
}

export function SheetFooter({ className = '', children, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2 gap-2 pt-4 border-t border-border/60 mt-auto ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export default Sheet;
