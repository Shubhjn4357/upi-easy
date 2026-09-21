// shadcn/ui Badge component
import React from 'react';

export function Badge({ className = "", variant = "default", children, ...props }) {
  const baseClasses =
    "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11px] font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2";

  const variantClasses = {
    default:
      "border-transparent bg-primary text-primary-foreground hover:bg-primary/80",
    secondary:
      "border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/80",
    destructive:
      "border-transparent bg-destructive/15 text-destructive border-destructive/20",
    outline: "text-foreground",
    success:
      "border-emerald-500/20 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400",
    warning:
      "border-amber-500/20 bg-amber-500/10 text-amber-600 dark:text-amber-400",
    cyan:
      "border-cyan-500/20 bg-cyan-500/10 text-cyan-600 dark:text-cyan-400",
  };

  const vClass = variantClasses[variant] || variantClasses.default;

  return (
    <div className={`${baseClasses} ${vClass} ${className}`} {...props}>
      {children}
    </div>
  );
}

export default Badge;
