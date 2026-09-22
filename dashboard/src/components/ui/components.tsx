// shadcn/ui Skeleton, Avatar, Separator, Alert, and Tabs components with strict TypeScript types
import React from 'react';

export { Input, Label } from './input';

// Skeleton
export interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
}

export function Skeleton({ className = '', ...props }: SkeletonProps) {
  return (
    <div
      className={`animate-pulse rounded-md bg-muted/60 ${className}`}
      {...props}
    />
  );
}

// Avatar
export interface AvatarProps extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
  children?: React.ReactNode;
}

export function Avatar({ className = '', children, ...props }: AvatarProps) {
  return (
    <div
      className={`relative flex h-9 w-9 shrink-0 overflow-hidden rounded-full border border-border ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export interface AvatarImageProps extends React.ImgHTMLAttributes<HTMLImageElement> {
  src?: string;
  alt?: string;
  className?: string;
}

export function AvatarImage({ src, alt = '', className = '', ...props }: AvatarImageProps) {
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

export interface AvatarFallbackProps extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
  children?: React.ReactNode;
}

export function AvatarFallback({ className = '', children, ...props }: AvatarFallbackProps) {
  return (
    <div
      className={`flex h-full w-full items-center justify-center rounded-full bg-muted font-bold text-xs text-muted-foreground ${className}`}
      {...props}>
      {children}
    </div>
  );
}

// Separator
export interface SeparatorProps extends React.HTMLAttributes<HTMLDivElement> {
  orientation?: 'horizontal' | 'vertical';
  className?: string;
}

export function Separator({
  orientation = 'horizontal',
  className = '',
  ...props
}: SeparatorProps) {
  return (
    <div
      role="separator"
      aria-orientation={orientation}
      className={`shrink-0 bg-border ${
        orientation === 'horizontal' ? 'h-[1px] w-full' : 'h-full w-[1px]'
      } ${className}`}
      {...props}
    />
  );
}

// Alert
export type AlertVariant = 'default' | 'destructive' | 'success';

export interface AlertProps extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
  variant?: AlertVariant;
  children?: React.ReactNode;
}

export function Alert({ className = '', variant = 'default', children, ...props }: AlertProps) {
  const variantClasses: Record<AlertVariant, string> = {
    default: 'bg-background text-foreground border-border',
    destructive:
      'border-destructive/50 text-destructive bg-destructive/10 dark:border-destructive',
    success:
      'border-emerald-500/30 text-emerald-600 dark:text-emerald-400 bg-emerald-500/10',
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

export function AlertTitle({ className = '', children, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h5
      className={`mb-1 font-semibold leading-none tracking-tight ${className}`}
      {...props}>
      {children}
    </h5>
  );
}

export function AlertDescription({ className = '', children, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return (
    <div
      className={`text-xs text-muted-foreground [&_p]:leading-relaxed ${className}`}
      {...props}>
      {children}
    </div>
  );
}

// Tabs
export interface TabsProps {
  value: string;
  onValueChange: (value: string) => void;
  className?: string;
  children?: React.ReactNode;
}

export function Tabs({ value, onValueChange, className = '', children }: TabsProps) {
  return (
    <div className={`space-y-4 ${className}`}>
      {React.Children.map(children, (child) => {
        if (!React.isValidElement(child)) return null;
        return React.cloneElement(child as React.ReactElement<any>, { activeTab: value, setActiveTab: onValueChange });
      })}
    </div>
  );
}

export interface TabsListProps {
  className?: string;
  activeTab?: string;
  setActiveTab?: (value: string) => void;
  children?: React.ReactNode;
}

export function TabsList({ className = '', activeTab, setActiveTab, children }: TabsListProps) {
  return (
    <div
      className={`inline-flex h-10 items-center justify-center rounded-xl bg-muted p-1 text-muted-foreground ${className}`}>
      {React.Children.map(children, (child) => {
        if (!React.isValidElement(child)) return null;
        return React.cloneElement(child as React.ReactElement<any>, { activeTab, setActiveTab });
      })}
    </div>
  );
}

export interface TabsTriggerProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  value: string;
  activeTab?: string;
  setActiveTab?: (value: string) => void;
  className?: string;
  children?: React.ReactNode;
}

export function TabsTrigger({
  value,
  activeTab,
  setActiveTab,
  className = '',
  children,
  ...props
}: TabsTriggerProps) {
  const isSelected = activeTab === value;
  return (
    <button
      type="button"
      role="tab"
      aria-selected={isSelected}
      onClick={() => setActiveTab?.(value)}
      className={`inline-flex items-center justify-center whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-semibold ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 ${
        isSelected
          ? 'bg-background text-foreground shadow-sm'
          : 'hover:text-foreground'
      } ${className}`}
      {...props}>
      {children}
    </button>
  );
}

export interface TabsContentProps {
  value: string;
  activeTab?: string;
  className?: string;
  children?: React.ReactNode;
}

export function TabsContent({ value, activeTab, className = '', children }: TabsContentProps) {
  if (activeTab !== value) return null;
  return (
    <div
      role="tabpanel"
      className={`ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${className}`}>
      {children}
    </div>
  );
}

// Suspense Fallback Loader
export interface SuspenseFallbackProps {
  activeTab?: string;
}

export function SuspenseFallback({ activeTab = 'overview' }: SuspenseFallbackProps) {
  return (
    <div className="flex flex-col items-center justify-center min-h-[300px] space-y-4 animate-pulse">
      <div className="w-10 h-10 rounded-2xl bg-brand-500/10 border border-brand-500/20 flex items-center justify-center">
        <div className="w-5 h-5 rounded-full border-2 border-brand-500 border-t-transparent animate-spin" />
      </div>
      <p className="text-xs text-muted-foreground font-mono">Loading {activeTab}...</p>
    </div>
  );
}
