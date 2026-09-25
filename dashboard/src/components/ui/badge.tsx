// Modern Custom Badge component with pulse dots and rich color variants
import React from 'react';

export type BadgeVariant =
  | 'default'
  | 'secondary'
  | 'destructive'
  | 'outline'
  | 'success'
  | 'warning'
  | 'cyan'
  | 'brand';

export type BadgeSize = 'sm' | 'default' | 'lg';

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
  variant?: BadgeVariant;
  size?: BadgeSize;
  dot?: boolean;
  pulse?: boolean;
  children?: React.ReactNode;
}

export function Badge({
  className = '',
  variant = 'default',
  size = 'default',
  dot = false,
  pulse = false,
  children,
  ...props
}: BadgeProps) {
  const baseClasses =
    'inline-flex items-center gap-1.5 font-semibold transition-colors focus:outline-none select-none';

  const sizeClasses: Record<BadgeSize, string> = {
    sm: 'rounded-md px-1.5 py-0.5 text-[10px]',
    default: 'rounded-full px-2.5 py-0.5 text-[11px]',
    lg: 'rounded-full px-3 py-1 text-xs',
  };

  const variantClasses: Record<BadgeVariant, { container: string; dot: string }> = {
    default: {
      container: 'border-transparent bg-primary text-primary-foreground hover:bg-primary/80',
      dot: 'bg-primary-foreground',
    },
    secondary: {
      container: 'border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/80',
      dot: 'bg-secondary-foreground',
    },
    destructive: {
      container: 'border-destructive/20 bg-destructive/10 text-destructive',
      dot: 'bg-destructive',
    },
    outline: {
      container: 'border-border text-foreground bg-background/50',
      dot: 'bg-foreground',
    },
    success: {
      container: 'border-emerald-500/20 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400',
      dot: 'bg-emerald-500',
    },
    warning: {
      container: 'border-amber-500/20 bg-amber-500/10 text-amber-600 dark:text-amber-400',
      dot: 'bg-amber-500',
    },
    cyan: {
      container: 'border-cyan-500/20 bg-cyan-500/10 text-cyan-600 dark:text-cyan-400',
      dot: 'bg-cyan-500',
    },
    brand: {
      container: 'border-brand-500/25 bg-brand-500/10 text-brand-600 dark:text-brand-400',
      dot: 'bg-brand-500',
    },
  };

  const v = variantClasses[variant] || variantClasses.default;
  const s = sizeClasses[size] || sizeClasses.default;

  return (
    <div className={`border ${baseClasses} ${v.container} ${s} ${className}`} {...props}>
      {dot && (
        <span className="relative flex h-1.5 w-1.5 shrink-0">
          {pulse && (
            <span
              className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${v.dot}`}
            />
          )}
          <span className={`relative inline-flex rounded-full h-1.5 w-1.5 ${v.dot}`} />
        </span>
      )}
      {children}
    </div>
  );
}

export default Badge;
