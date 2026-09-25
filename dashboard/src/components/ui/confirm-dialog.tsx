// Reusable Confirm Dialog for delete actions, alerts, and critical confirmations
import React, { useState } from 'react';
import { Dialog, DialogContent } from './dialog';
import { Button } from './button';
import { IconTrash } from './icons';

export type ConfirmDialogVariant = 'danger' | 'warning' | 'info' | 'success';

export interface ConfirmDialogProps {
  open: boolean;
  onOpenChange?: (open: boolean) => void;
  title: string;
  description: React.ReactNode;
  confirmText?: string;
  cancelText?: string;
  variant?: ConfirmDialogVariant;
  loading?: boolean;
  onConfirm: () => void | Promise<void>;
  onCancel?: () => void;
}

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  variant = 'danger',
  loading = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const [internalLoading, setInternalLoading] = useState(false);
  const isLoading = loading || internalLoading;

  const handleClose = () => {
    if (isLoading) return;
    onCancel?.();
    onOpenChange?.(false);
  };

  const handleConfirm = async () => {
    try {
      setInternalLoading(true);
      await onConfirm();
      onOpenChange?.(false);
    } catch {
      // Handled by caller
    } finally {
      setInternalLoading(false);
    }
  };

  const variantStyles: Record<
    ConfirmDialogVariant,
    { iconBg: string; iconBorder: string; iconColor: string; buttonVariant: 'destructive' | 'brand' | 'default' }
  > = {
    danger: {
      iconBg: 'bg-rose-500/10 dark:bg-rose-500/15',
      iconBorder: 'border-rose-500/25',
      iconColor: 'text-rose-600 dark:text-rose-400',
      buttonVariant: 'destructive',
    },
    warning: {
      iconBg: 'bg-amber-500/10 dark:bg-amber-500/15',
      iconBorder: 'border-amber-500/25',
      iconColor: 'text-amber-600 dark:text-amber-400',
      buttonVariant: 'brand',
    },
    info: {
      iconBg: 'bg-blue-500/10 dark:bg-blue-500/15',
      iconBorder: 'border-blue-500/25',
      iconColor: 'text-blue-600 dark:text-blue-400',
      buttonVariant: 'default',
    },
    success: {
      iconBg: 'bg-emerald-500/10 dark:bg-emerald-500/15',
      iconBorder: 'border-emerald-500/25',
      iconColor: 'text-emerald-600 dark:text-emerald-400',
      buttonVariant: 'brand',
    },
  };

  const currentStyle = variantStyles[variant];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="sm" className="p-6 text-center sm:text-left">
        <div className="flex flex-col sm:flex-row items-center sm:items-start gap-4">
          {/* Animated Icon Badge */}
          <div
            className={`w-12 h-12 rounded-2xl flex items-center justify-center shrink-0 border shadow-inner ${currentStyle.iconBg} ${currentStyle.iconBorder} ${currentStyle.iconColor}`}>
            {variant === 'danger' ? (
              <IconTrash className="w-5 h-5" />
            ) : (
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth="2"
                  d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
                />
              </svg>
            )}
          </div>

          <div className="space-y-1.5 flex-1">
            <h3 className="text-base font-bold text-foreground tracking-tight">{title}</h3>
            <div className="text-xs text-muted-foreground leading-relaxed">
              {description}
            </div>
          </div>
        </div>

        <div className="mt-6 flex flex-col-reverse sm:flex-row sm:justify-end gap-2.5">
          <Button
            type="button"
            variant="outline"
            size="default"
            disabled={isLoading}
            onClick={handleClose}
            className="w-full sm:w-auto">
            {cancelText}
          </Button>
          <Button
            type="button"
            variant={currentStyle.buttonVariant}
            size="default"
            loading={isLoading}
            onClick={handleConfirm}
            className="w-full sm:w-auto min-w-[100px]">
            {confirmText}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

export default ConfirmDialog;
