// Modern custom Input, Textarea, and Form components with icons, clear button, and error states
import React, { forwardRef, useState } from 'react';
import { IconX, IconSearch } from './icons';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  helperText?: string;
  error?: string;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  clearable?: boolean;
  onClear?: () => void;
  wrapperClassName?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  (
    {
      className = '',
      wrapperClassName = '',
      type = 'text',
      label,
      helperText,
      error,
      leftIcon,
      rightIcon,
      clearable,
      onClear,
      value,
      onChange,
      disabled,
      required,
      id,
      ...props
    },
    ref
  ) => {
    const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);
    const hasValue = value !== undefined && value !== null && String(value).length > 0;

    return (
      <div className={`w-full space-y-1.5 ${wrapperClassName}`}>
        {label && (
          <div className="flex items-center justify-between">
            <label
              htmlFor={inputId}
              className="text-xs font-semibold leading-none text-foreground/90 select-none flex items-center gap-1">
              <span>{label}</span>
              {required && <span className="text-destructive font-bold">*</span>}
            </label>
          </div>
        )}

        <div className="relative flex items-center">
          {leftIcon && (
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-muted-foreground transition-colors group-focus-within:text-foreground">
              {leftIcon}
            </div>
          )}

          <input
            ref={ref}
            id={inputId}
            type={type}
            value={value}
            onChange={onChange}
            disabled={disabled}
            required={required}
            className={`flex h-9 w-full rounded-xl border bg-background/90 px-3 py-1.5 text-xs text-foreground shadow-sm transition-all duration-200 placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/25 focus-visible:border-primary disabled:cursor-not-allowed disabled:opacity-50 disabled:bg-muted/40 ${
              leftIcon ? 'pl-9' : ''
            } ${clearable || rightIcon ? 'pr-9' : ''} ${
              error
                ? 'border-destructive focus-visible:ring-destructive/25 focus-visible:border-destructive text-destructive'
                : 'border-input hover:border-muted-foreground/30'
            } ${className}`}
            {...props}
          />

          {clearable && hasValue && !disabled && (
            <button
              type="button"
              onClick={onClear}
              className="absolute inset-y-0 right-0 pr-3 flex items-center text-muted-foreground hover:text-foreground transition-colors"
              tabIndex={-1}
              aria-label="Clear input">
              <IconX className="w-3.5 h-3.5" />
            </button>
          )}

          {rightIcon && (!clearable || !hasValue) && (
            <div className="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none text-muted-foreground">
              {rightIcon}
            </div>
          )}
        </div>

        {error && (
          <p className="text-[11px] font-medium text-destructive flex items-center gap-1 animate-fade-in">
            <svg
              className="w-3 h-3 shrink-0"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            <span>{error}</span>
          </p>
        )}

        {!error && helperText && (
          <p className="text-[11px] text-muted-foreground">{helperText}</p>
        )}
      </div>
    );
  }
);
Input.displayName = 'Input';

export interface LabelProps extends React.LabelHTMLAttributes<HTMLLabelElement> {
  className?: string;
  required?: boolean;
  children?: React.ReactNode;
}

export function Label({ className = '', required, children, ...props }: LabelProps) {
  return (
    <label
      className={`text-xs font-semibold leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 text-foreground/90 select-none flex items-center gap-1 ${className}`}
      {...props}>
      {children}
      {required && <span className="text-destructive font-bold">*</span>}
    </label>
  );
}

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  helperText?: string;
  error?: string;
  wrapperClassName?: string;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  (
    {
      className = '',
      wrapperClassName = '',
      label,
      helperText,
      error,
      disabled,
      required,
      id,
      rows = 3,
      ...props
    },
    ref
  ) => {
    const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

    return (
      <div className={`w-full space-y-1.5 ${wrapperClassName}`}>
        {label && (
          <div className="flex items-center justify-between">
            <label
              htmlFor={inputId}
              className="text-xs font-semibold leading-none text-foreground/90 select-none flex items-center gap-1">
              <span>{label}</span>
              {required && <span className="text-destructive font-bold">*</span>}
            </label>
          </div>
        )}

        <textarea
          ref={ref}
          id={inputId}
          rows={rows}
          disabled={disabled}
          required={required}
          className={`flex w-full rounded-xl border bg-background/90 px-3 py-2 text-xs text-foreground shadow-sm transition-all duration-200 placeholder:text-muted-foreground/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/25 focus-visible:border-primary disabled:cursor-not-allowed disabled:opacity-50 ${
            error
              ? 'border-destructive focus-visible:ring-destructive/25 focus-visible:border-destructive text-destructive'
              : 'border-input hover:border-muted-foreground/30'
          } ${className}`}
          {...props}
        />

        {error && (
          <p className="text-[11px] font-medium text-destructive flex items-center gap-1 animate-fade-in">
            <span>{error}</span>
          </p>
        )}

        {!error && helperText && (
          <p className="text-[11px] text-muted-foreground">{helperText}</p>
        )}
      </div>
    );
  }
);
Textarea.displayName = 'Textarea';

export interface SearchInputProps extends Omit<InputProps, 'leftIcon'> {
  onSearch?: (query: string) => void;
}

export function SearchInput({
  className = '',
  placeholder = 'Search...',
  value,
  onChange,
  onClear,
  ...props
}: SearchInputProps) {
  const [internalValue, setInternalValue] = useState('');
  const currentValue = value !== undefined ? String(value) : internalValue;

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (value === undefined) {
      setInternalValue(e.target.value);
    }
    onChange?.(e);
  };

  const handleClear = () => {
    if (value === undefined) {
      setInternalValue('');
    }
    onClear?.();
  };

  return (
    <Input
      type="text"
      placeholder={placeholder}
      value={currentValue}
      onChange={handleChange}
      clearable={Boolean(currentValue)}
      onClear={handleClear}
      leftIcon={<IconSearch className="w-3.5 h-3.5 text-muted-foreground" />}
      className={`bg-muted/40 hover:bg-background focus:bg-background rounded-xl ${className}`}
      {...props}
    />
  );
}

export default Input;
