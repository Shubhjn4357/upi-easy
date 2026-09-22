import React from 'react';
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { IconX, IconArrowUpRight } from '@/components/ui/icons';

export interface BottomSheetActionItem {
  label: string;
  onClick: () => void;
  variant?: 'default' | 'danger' | 'destructive' | 'success' | 'outline' | 'secondary';
  icon?: React.ReactNode | React.ComponentType<{ className?: string }>;
}

export interface BottomSheetProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  subtitle?: string;
  actions?: BottomSheetActionItem[];
  children?: React.ReactNode;
}

function renderActionIcon(icon?: React.ReactNode | React.ComponentType<{ className?: string }>) {
  if (!icon) return null;
  if (React.isValidElement(icon)) return icon;
  if (typeof icon === 'function') {
    const IconComp = icon as React.ComponentType<{ className?: string }>;
    return <IconComp className="w-4 h-4 text-muted-foreground" />;
  }
  return <span className="text-sm">{icon}</span>;
}

// shadcn/ui Action Bottom Sheet / Drawer Component with strict TypeScript types
export function BottomSheet({
  isOpen,
  onClose,
  title = '',
  subtitle,
  actions = [],
  children,
}: BottomSheetProps) {
  if (!isOpen) return null;

  return (
    <Sheet open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="bottom" className="sm:max-w-lg sm:mx-auto sm:rounded-2xl sm:bottom-6 sm:border">
        {/* Header */}
        <SheetHeader className="pb-3 border-b border-border flex flex-row items-center justify-between">
          <div>
            <SheetTitle>{title}</SheetTitle>
            {subtitle && <SheetDescription>{subtitle}</SheetDescription>}
          </div>
          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            className="h-8 w-8 rounded-lg text-muted-foreground hover:text-foreground">
            <IconX className="w-4 h-4" />
          </Button>
        </SheetHeader>

        {/* Action Buttons List */}
        {actions.length > 0 && (
          <div className="py-3 space-y-2">
            {actions.map((act, i) => (
              <Button
                key={i}
                variant={
                  act.variant === 'danger' || act.variant === 'destructive'
                    ? 'destructive'
                    : act.variant === 'success'
                    ? 'outline'
                    : 'secondary'
                }
                onClick={() => {
                  onClose();
                  act.onClick();
                }}
                className={`w-full justify-between h-10 px-4 rounded-xl ${
                  act.variant === 'success'
                    ? 'border-emerald-500/20 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/20'
                    : ''
                }`}>
                <div className="flex items-center gap-2.5">
                  <span className="flex items-center justify-center w-5 h-5">{renderActionIcon(act.icon)}</span>
                  <span>{act.label}</span>
                </div>
                <IconArrowUpRight className="w-3.5 h-3.5 opacity-60" />
              </Button>
            ))}
          </div>
        )}

        {/* Custom Body Content */}
        {children && <div className="py-2 overflow-y-auto max-h-[60vh]">{children}</div>}
      </SheetContent>
    </Sheet>
  );
}

export default BottomSheet;
