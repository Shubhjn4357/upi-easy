// shadcn/ui Sheet / Bottom Sheet component
import React from 'react';

export function Sheet({ open, onOpenChange, children }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 overflow-hidden">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/60 backdrop-blur-sm transition-opacity"
        onClick={() => onOpenChange?.(false)}
      />
      {children}
    </div>
  );
}

export function SheetContent({
  side = "bottom",
  className = "",
  children,
  onClose,
  ...props
}) {
  const sideClasses = {
    top: "inset-x-0 top-0 border-b animate-in slide-in-from-top duration-300",
    bottom:
      "inset-x-0 bottom-0 border-t rounded-t-3xl max-h-[85vh] overflow-y-auto animate-bottom-sheet-up",
    left: "inset-y-0 left-0 h-full w-3/4 max-w-sm border-r animate-in slide-in-from-left duration-300",
    right:
      "inset-y-0 right-0 h-full w-3/4 max-w-sm border-l animate-in slide-in-from-right duration-300",
  };

  return (
    <div
      className={`fixed z-50 gap-4 bg-card p-6 shadow-2xl transition ease-in-out border-border text-card-foreground ${sideClasses[side] || sideClasses.bottom} ${className}`}
      {...props}>
      {/* Mobile drag handle */}
      {side === "bottom" && (
        <div className="w-12 h-1.5 rounded-full bg-muted-foreground/25 mx-auto -mt-2 mb-4"></div>
      )}
      {children}
    </div>
  );
}

export function SheetHeader({ className = "", children, ...props }) {
  return (
    <div
      className={`flex flex-col space-y-1.5 text-left mb-4 ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export function SheetTitle({ className = "", children, ...props }) {
  return (
    <h3
      className={`text-base font-bold leading-none tracking-tight text-foreground ${className}`}
      {...props}>
      {children}
    </h3>
  );
}

export function SheetDescription({ className = "", children, ...props }) {
  return (
    <p
      className={`text-xs text-muted-foreground ${className}`}
      {...props}>
      {children}
    </p>
  );
}

export function SheetFooter({ className = "", children, ...props }) {
  return (
    <div
      className={`flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2 gap-2 mt-6 ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export default Sheet;
