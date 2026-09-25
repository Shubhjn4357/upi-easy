// Modern Tactile Button component with loading spinner, icon slots, and micro-interactions
import React from 'react';

export type ButtonVariant =
  | 'default'
  | 'destructive'
  | 'outline'
  | 'secondary'
  | 'ghost'
  | 'link'
  | 'brand'
  | 'glass';

export type ButtonSize = 'xs' | 'sm' | 'default' | 'lg' | 'icon';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  loadingText?: string;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  className?: string;
  children?: React.ReactNode;
}

export function Button({
  className = '',
  variant = 'default',
  size = 'default',
  type = 'button',
  loading = false,
  loadingText,
  leftIcon,
  rightIcon,
  children,
  disabled = false,
  ...props
}: ButtonProps) {
  const baseClasses =
    'inline-flex items-center justify-center whitespace-nowrap rounded-xl text-xs font-semibold ring-offset-background transition-all duration-150 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 select-none active:scale-[0.98] cursor-pointer';

  const variantClasses: Record<ButtonVariant, string> = {
    default: 'bg-primary text-primary-foreground hover:bg-primary/90 shadow-sm hover:shadow active:bg-primary/95',
    destructive: 'bg-destructive text-destructive-foreground hover:bg-destructive/90 shadow-sm hover:shadow-destructive/20 active:bg-destructive/95',
    outline: 'border border-input bg-background/80 hover:bg-accent hover:text-accent-foreground active:bg-accent/80',
    secondary: 'bg-secondary text-secondary-foreground hover:bg-secondary/80 active:bg-secondary/90',
    ghost: 'hover:bg-accent hover:text-accent-foreground active:bg-accent/70',
    link: 'text-primary underline-offset-4 hover:underline active:scale-100',
    brand: 'bg-gradient-to-r from-brand-600 to-indigo-600 hover:from-brand-500 hover:to-indigo-500 text-white shadow-md shadow-brand-500/25 hover:shadow-lg hover:shadow-brand-500/35 active:from-brand-700 active:to-indigo-700',
    glass: 'glass-panel text-foreground hover:bg-card/90 shadow-sm hover:shadow active:bg-card/75',
  };

  const sizeClasses: Record<ButtonSize, string> = {
    xs: 'h-7 rounded-lg px-2.5 text-[10px] gap-1.5',
    sm: 'h-8 rounded-lg px-3 text-[11px] gap-1.5',
    default: 'h-9 px-4 py-2 gap-2',
    lg: 'h-11 rounded-xl px-6 text-sm gap-2.5 font-bold',
    icon: 'h-9 w-9 p-0',
  };

  const vClass = variantClasses[variant] || variantClasses.default;
  const sClass = sizeClasses[size] || sizeClasses.default;
  const isDisabled = disabled || loading;

  return (
    <button
      type={type}
      disabled={isDisabled}
      className={`${baseClasses} ${vClass} ${sClass} ${className}`}
      {...props}>
      {loading ? (
        <>
          <svg
            className="animate-spin -ml-0.5 mr-1.5 h-3.5 w-3.5 text-current"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24">
            <circle
              className="opacity-25"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeWidth="4"
            />
            <path
              className="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
            />
          </svg>
          {loadingText ? <span>{loadingText}</span> : children}
        </>
      ) : (
        <>
          {leftIcon && <span className="inline-flex shrink-0">{leftIcon}</span>}
          {children}
          {rightIcon && <span className="inline-flex shrink-0">{rightIcon}</span>}
        </>
      )}
    </button>
  );
}

export default Button;
