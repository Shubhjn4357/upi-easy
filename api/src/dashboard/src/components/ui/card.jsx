// shadcn/ui Card components
import React from 'react';

export function Card({ className = "", children, ...props }) {
  return (
    <div
      className={`rounded-2xl border bg-card text-card-foreground shadow-sm transition-colors ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export function CardHeader({ className = "", children, ...props }) {
  return (
    <div
      className={`flex flex-col space-y-1.5 p-5 sm:p-6 ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export function CardTitle({ className = "", children, ...props }) {
  return (
    <h3
      className={`text-base sm:text-lg font-bold leading-none tracking-tight text-foreground ${className}`}
      {...props}>
      {children}
    </h3>
  );
}

export function CardDescription({ className = "", children, ...props }) {
  return (
    <p
      className={`text-xs text-muted-foreground ${className}`}
      {...props}>
      {children}
    </p>
  );
}

export function CardContent({ className = "", children, ...props }) {
  return (
    <div className={`p-5 sm:p-6 pt-0 ${className}`} {...props}>
      {children}
    </div>
  );
}

export function CardFooter({ className = "", children, ...props }) {
  return (
    <div
      className={`flex items-center p-5 sm:p-6 pt-0 ${className}`}
      {...props}>
      {children}
    </div>
  );
}

export default Card;
