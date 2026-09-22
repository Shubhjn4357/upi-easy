// shadcn/ui Button component with strict TypeScript types
import React from 'react';

export type ButtonVariant =
  | 'default'
  | 'destructive'
  | 'outline'
  | 'secondary'
  | 'ghost'
  | 'link'
  | 'brand';

export type ButtonSize = 'default' | 'sm' | 'lg' | 'icon';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  className?: string;
  children?: React.ReactNode;
}

export function Button({
  className = '',
  variant = 'default',
  size = 'default',
  type = 'button',
  children,
  disabled = false,
  ...props
}: ButtonProps) {
  const baseClasses =
    'inline-flex items-center justify-center whitespace-nowrap rounded-xl text-xs font-semibold ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 select-none';

  const variantClasses: Record<ButtonVariant, string> = {
    default: 'bg-primary text-primary-foreground hover:bg-primary/90 shadow-sm',
    destructive: 'bg-destructive text-destructive-foreground hover:bg-destructive/90 shadow-sm',
    outline: 'border border-input bg-background hover:bg-accent hover:text-accent-foreground',
    secondary: 'bg-secondary text-secondary-foreground hover:bg-secondary/80',
    ghost: 'hover:bg-accent hover:text-accent-foreground',
    link: 'text-primary underline-offset-4 hover:underline',
    brand: 'bg-brand-600 hover:bg-brand-500 text-white shadow-md shadow-brand-500/20',
  };

  const sizeClasses: Record<ButtonSize, string> = {
    default: 'h-9 px-4 py-2',
    sm: 'h-8 rounded-lg px-3 text-[11px]',
    lg: 'h-10 rounded-xl px-8 text-sm',
    icon: 'h-9 w-9 p-0',
  };

  const vClass = variantClasses[variant] || variantClasses.default;
  const sClass = sizeClasses[size] || sizeClasses.default;

  return (
    <button
      type={type}
      disabled={disabled}
      className={`${baseClasses} ${vClass} ${sClass} ${className}`}
      {...props}>
      {children}
    </button>
  );
}

export default Button;
