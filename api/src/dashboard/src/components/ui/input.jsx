// shadcn/ui Input and Label components
import React from 'react';

export function Input({ className = "", type = "text", ...props }) {
  return (
    <input
      type={type}
      className={`flex h-9 w-full rounded-xl border border-input bg-background px-3 py-1 text-xs text-foreground shadow-sm transition-colors file:border-0 file:bg-transparent file:text-xs file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
      {...props}
    />
  );
}

export function Label({ className = "", children, ...props }) {
  return (
    <label
      className={`text-xs font-semibold leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 text-foreground select-none ${className}`}
      {...props}>
      {children}
    </label>
  );
}

export default Input;
