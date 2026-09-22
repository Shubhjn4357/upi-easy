// shadcn/ui Dialog components with strict TypeScript types
import React from 'react';

export interface DialogProps {
  open: boolean;
  onOpenChange?: (open: boolean) => void;
  children: React.ReactNode;
}

export function Dialog({ open, onOpenChange, children }: DialogProps) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/60 backdrop-blur-sm transition-opacity animate-fade-in"
        onClick={() => onOpenChange?.(false)}
      />
      {/* Content wrapper */}
      <div className="relative z-50 w-full max-w-lg animate-in zoom-in-95 duration-200">
        {children}
      </div>
    </div>
  );
}

export interface DialogContentProps extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
  children?: React.ReactNode;
}

export function DialogContent({ className = '', children, ...props }: DialogContentProps) {
  return (
    <div
      className={`relative w-full rounded-2xl border border-border bg-card p-6 shadow-2xl text-card-foreground ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export function DialogHeader({ className = '', children, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`flex flex-col space-y-1.5 text-left mb-4 ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export function DialogTitle({ className = '', children, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3
      className={`text-lg font-bold leading-none tracking-tight text-foreground ${className}`}
      {...props}>
      {children}
    </h3>
  );
}

export function DialogDescription({ className = '', children, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return (
    <p
      className={`text-xs text-muted-foreground mt-1 ${className}`}
      {...props}>
      {children}
    </p>
  );
}

export function DialogFooter({ className = '', children, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2 gap-2 mt-6 ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export default Dialog;
