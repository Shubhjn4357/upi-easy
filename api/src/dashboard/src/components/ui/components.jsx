// shadcn/ui Skeleton, Avatar, Separator, Alert, and Tabs components
import React from 'react';

// Skeleton
export function Skeleton({ className = "", ...props }) {
  return (
    <div
      className={`animate-pulse rounded-md bg-muted/60 ${className}`}
      {...props}
    />
  );
}

// Avatar
export function Avatar({ className = "", children, ...props }) {
  return (
    <div
      className={`relative flex h-9 w-9 shrink-0 overflow-hidden rounded-full border border-border ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export function AvatarImage({ src, alt = "", className = "", ...props }) {
  if (!src) return null;
  return (
    <img
      src={src}
      alt={alt}
      className={`aspect-square h-full w-full object-cover ${className}`}
      {...props}
    />
  );
}

export function AvatarFallback({ className = "", children, ...props }) {
  return (
    <div
      className={`flex h-full w-full items-center justify-center rounded-full bg-muted font-bold text-xs text-muted-foreground ${className}`}
      {...props}>
      {children}
    </div>
  );
}

// Separator
export function Separator({
  orientation = "horizontal",
  className = "",
  ...props
}) {
  return (
    <div
      role="separator"
      aria-orientation={orientation}
      className={`shrink-0 bg-border ${
        orientation === "horizontal" ? "h-[1px] w-full" : "h-full w-[1px]"
      } ${className}`}
      {...props}
    />
  );
}

// Alert
export function Alert({ className = "", variant = "default", children, ...props }) {
  const variantClasses = {
    default: "bg-background text-foreground border-border",
    destructive:
      "border-destructive/50 text-destructive bg-destructive/10 dark:border-destructive",
    success:
      "border-emerald-500/30 text-emerald-600 dark:text-emerald-400 bg-emerald-500/10",
  };
  return (
    <div
      role="alert"
      className={`relative w-full rounded-xl border p-4 text-xs [&>svg]:absolute [&>svg]:left-4 [&>svg]:top-4 [&>svg+div]:translate-y-[-3px] [&:has(svg)]:pl-11 ${
        variantClasses[variant] || variantClasses.default
      } ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export function AlertTitle({ className = "", children, ...props }) {
  return (
    <h5
      className={`mb-1 font-semibold leading-none tracking-tight ${className}`}
      {...props}>
      {children}
    </h5>
  );
}

export function AlertDescription({ className = "", children, ...props }) {
  return (
    <div
      className={`text-xs text-muted-foreground [&_p]:leading-relaxed ${className}`}
      {...props}>
      {children}
    </div>
  );
}

// Tabs
export function Tabs({ value, onValueChange, className = "", children }) {
  return (
    <div className={`space-y-4 ${className}`}>
      {React.Children.map(children, (child) => {
        if (!child) return null;
        return React.cloneElement(child, { activeTab: value, setActiveTab: onValueChange });
      })}
    </div>
  );
}

export function TabsList({ className = "", activeTab, setActiveTab, children }) {
  return (
    <div
      className={`inline-flex h-10 items-center justify-center rounded-xl bg-muted p-1 text-muted-foreground ${className}`}>
      {React.Children.map(children, (child) => {
        if (!child) return null;
        return React.cloneElement(child, { activeTab, setActiveTab });
      })}
    </div>
  );
}

export function TabsTrigger({
  value,
  activeTab,
  setActiveTab,
  className = "",
  children,
  ...props
}) {
  const isSelected = activeTab === value;
  return (
    <button
      type="button"
      role="tab"
      aria-selected={isSelected}
      onClick={() => setActiveTab?.(value)}
      className={`inline-flex items-center justify-center whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-semibold ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 ${
        isSelected
          ? "bg-background text-foreground shadow-sm"
          : "hover:text-foreground"
      } ${className}`}
      {...props}>
      {children}
    </button>
  );
}

export function TabsContent({ value, activeTab, className = "", children }) {
  if (activeTab !== value) return null;
  return (
    <div
      role="tabpanel"
      className={`ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${className}`}>
      {children}
    </div>
  );
}
