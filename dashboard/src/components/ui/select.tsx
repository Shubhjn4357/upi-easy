// Modern Custom Select & NativeSelect with smooth dropdown, keyboard navigation, and form compatibility
import React, { useState, useRef, useEffect, forwardRef } from 'react';
import { IconChevronDown, IconCheck, IconSearch } from './icons';

export interface SelectOption {
  value: string;
  label: string;
  icon?: React.ReactNode;
  description?: string;
  disabled?: boolean;
}

export interface CustomSelectProps {
  label?: string;
  name?: string;
  value?: string;
  defaultValue?: string;
  onChange?: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
  searchable?: boolean;
  searchPlaceholder?: string;
  error?: string;
  helperText?: string;
  className?: string;
  triggerClassName?: string;
  dropdownClassName?: string;
  leftIcon?: React.ReactNode;
}

export function Select({
  label,
  name,
  value,
  defaultValue,
  onChange,
  options = [],
  placeholder = 'Select an option...',
  disabled = false,
  required = false,
  searchable = false,
  searchPlaceholder = 'Search options...',
  error,
  helperText,
  className = '',
  triggerClassName = '',
  dropdownClassName = '',
  leftIcon,
}: CustomSelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [internalValue, setInternalValue] = useState<string>(
    value !== undefined ? value : defaultValue || ''
  );
  const [searchQuery, setSearchQuery] = useState('');
  const containerRef = useRef<HTMLDivElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  const selectedValue = value !== undefined ? value : internalValue;
  const selectedOption = options.find((opt) => opt.value === selectedValue);

  // Click outside to close
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [isOpen]);

  // Focus search input when opened
  useEffect(() => {
    if (isOpen && searchable && searchInputRef.current) {
      setTimeout(() => searchInputRef.current?.focus(), 50);
    } else {
      setSearchQuery('');
    }
  }, [isOpen, searchable]);

  // Keyboard navigation (Escape to close)
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape' && isOpen) {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      window.addEventListener('keydown', handleKeyDown);
      return () => window.removeEventListener('keydown', handleKeyDown);
    }
  }, [isOpen]);

  const handleSelect = (option: SelectOption) => {
    if (option.disabled || disabled) return;
    if (value === undefined) {
      setInternalValue(option.value);
    }
    onChange?.(option.value);
    setIsOpen(false);
  };

  const filteredOptions = searchable && searchQuery
    ? options.filter(
        (opt) =>
          opt.label.toLowerCase().includes(searchQuery.toLowerCase()) ||
          opt.value.toLowerCase().includes(searchQuery.toLowerCase()) ||
          (opt.description && opt.description.toLowerCase().includes(searchQuery.toLowerCase()))
      )
    : options;

  return (
    <div className={`w-full space-y-1.5 ${className}`} ref={containerRef}>
      {/* Hidden input for standard HTML form compatibility */}
      {name && <input type="hidden" name={name} value={selectedValue} />}

      {label && (
        <div className="flex items-center justify-between">
          <label className="text-xs font-semibold leading-none text-foreground/90 select-none flex items-center gap-1">
            <span>{label}</span>
            {required && <span className="text-destructive font-bold">*</span>}
          </label>
        </div>
      )}

      <div className="relative">
        {/* Trigger Button */}
        <button
          type="button"
          disabled={disabled}
          onClick={() => setIsOpen(!isOpen)}
          aria-haspopup="listbox"
          aria-expanded={isOpen}
          className={`flex h-9 w-full items-center justify-between rounded-xl border bg-background/90 px-3 py-1.5 text-xs text-foreground shadow-sm transition-all duration-150 focus:outline-none focus:ring-2 focus:ring-primary/25 focus:border-primary disabled:cursor-not-allowed disabled:opacity-50 ${
            error
              ? 'border-destructive focus:ring-destructive/25 focus:border-destructive'
              : isOpen
              ? 'border-primary ring-2 ring-primary/20'
              : 'border-input hover:border-muted-foreground/30'
          } ${triggerClassName}`}>
          <div className="flex items-center gap-2 truncate">
            {leftIcon && <span className="text-muted-foreground shrink-0">{leftIcon}</span>}
            {selectedOption?.icon && (
              <span className="shrink-0 flex items-center">{selectedOption.icon}</span>
            )}
            {selectedOption ? (
              <span className="truncate font-medium">{selectedOption.label}</span>
            ) : (
              <span className="text-muted-foreground/70 truncate">{placeholder}</span>
            )}
          </div>

          <IconChevronDown
            className={`w-3.5 h-3.5 text-muted-foreground transition-transform duration-200 shrink-0 ml-2 ${
              isOpen ? 'rotate-180 text-foreground' : ''
            }`}
          />
        </button>

        {/* Dropdown Menu */}
        {isOpen && (
          <div
            role="listbox"
            className={`glass-dropdown absolute z-50 mt-1.5 max-h-60 w-full overflow-hidden rounded-2xl border border-border/80 bg-popover/95 p-1 text-popover-foreground shadow-2xl backdrop-blur-xl animate-dropdown-in ${dropdownClassName}`}>
            {/* Search Input in Dropdown */}
            {searchable && (
              <div className="p-1.5 border-b border-border/60 mb-1">
                <div className="relative flex items-center">
                  <IconSearch className="w-3.5 h-3.5 absolute left-2.5 text-muted-foreground pointer-events-none" />
                  <input
                    ref={searchInputRef}
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder={searchPlaceholder}
                    className="w-full bg-muted/50 rounded-lg pl-8 pr-2.5 py-1 text-xs text-foreground placeholder:text-muted-foreground/60 focus:outline-none focus:ring-1 focus:ring-primary"
                  />
                </div>
              </div>
            )}

            <div className="max-h-48 overflow-y-auto space-y-0.5 p-0.5">
              {filteredOptions.length === 0 ? (
                <div className="py-4 text-center text-xs text-muted-foreground">
                  No options found
                </div>
              ) : (
                filteredOptions.map((opt) => {
                  const isSelected = opt.value === selectedValue;
                  return (
                    <div
                      key={opt.value}
                      role="option"
                      aria-selected={isSelected}
                      onClick={() => handleSelect(opt)}
                      className={`relative flex items-center justify-between rounded-xl px-3 py-2 text-xs select-none transition-all duration-100 ${
                        opt.disabled
                          ? 'opacity-40 cursor-not-allowed pointer-events-none'
                          : isSelected
                          ? 'bg-primary/10 text-primary font-semibold'
                          : 'cursor-pointer hover:bg-accent hover:text-accent-foreground text-foreground'
                      }`}>
                      <div className="flex items-center gap-2 truncate">
                        {opt.icon && <span className="shrink-0">{opt.icon}</span>}
                        <div className="flex flex-col truncate">
                          <span className="truncate">{opt.label}</span>
                          {opt.description && (
                            <span className="text-[10px] text-muted-foreground font-normal truncate">
                              {opt.description}
                            </span>
                          )}
                        </div>
                      </div>

                      {isSelected && (
                        <IconCheck className="w-3.5 h-3.5 text-primary shrink-0 ml-2" />
                      )}
                    </div>
                  );
                })
              )}
            </div>
          </div>
        )}
      </div>

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

// Styled Native Select component with modern custom chevron
export interface NativeSelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  helperText?: string;
  error?: string;
  wrapperClassName?: string;
}

export const NativeSelect = forwardRef<HTMLSelectElement, NativeSelectProps>(
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
      children,
      ...props
    },
    ref
  ) => {
    const selectId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

    return (
      <div className={`w-full space-y-1.5 ${wrapperClassName}`}>
        {label && (
          <div className="flex items-center justify-between">
            <label
              htmlFor={selectId}
              className="text-xs font-semibold leading-none text-foreground/90 select-none flex items-center gap-1">
              <span>{label}</span>
              {required && <span className="text-destructive font-bold">*</span>}
            </label>
          </div>
        )}

        <div className="relative">
          <select
            ref={ref}
            id={selectId}
            disabled={disabled}
            required={required}
            className={`flex h-9 w-full appearance-none rounded-xl border bg-background/90 px-3 py-1.5 pr-8 text-xs text-foreground shadow-sm transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/25 focus-visible:border-primary disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer ${
              error
                ? 'border-destructive focus-visible:ring-destructive/25 focus-visible:border-destructive text-destructive'
                : 'border-input hover:border-muted-foreground/30'
            } ${className}`}
            {...props}>
            {children}
          </select>
          <div className="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none text-muted-foreground">
            <IconChevronDown className="w-3.5 h-3.5" />
          </div>
        </div>

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
NativeSelect.displayName = 'NativeSelect';

export default Select;
