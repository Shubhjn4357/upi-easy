import React from 'react';
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription, type SheetSide } from '@/components/ui/sheet';
import { IconArrowUpRight } from '@/components/ui/icons';

export interface BottomSheetActionItem {
  label: string;
  onClick: () => void;
  variant?: 'default' | 'danger' | 'destructive' | 'success' | 'outline' | 'secondary' | 'brand';
  icon?: React.ReactNode | React.ComponentType<{ className?: string }>;
  description?: string;
}

export interface BottomSheetProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  subtitle?: string;
  icon?: React.ReactNode;
  side?: SheetSide;
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

// Modern Action Drawer / Bottom Sheet with smooth slide transitions and rich action items
export function BottomSheet({
  isOpen,
  onClose,
  title = '',
  subtitle,
  icon,
  side = 'bottom',
  actions = [],
  children,
}: BottomSheetProps) {
  if (!isOpen) return null;

  return (
    <Sheet open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <SheetContent
        side={side}
        onClose={onClose}
        className={
          side === 'bottom'
            ? 'sm:max-w-lg sm:mx-auto sm:rounded-3xl sm:bottom-6 sm:border sm:border-border/80'
            : 'max-w-md'
        }>
        {/* Header */}
        <SheetHeader className="pb-3 border-b border-border/60">
          <div className="flex items-center gap-3">
            {icon && (
              <div className="w-9 h-9 rounded-2xl bg-primary/10 border border-primary/20 flex items-center justify-center text-primary shrink-0">
                {icon}
              </div>
            )}
            <div className="pr-8">
              <SheetTitle>{title}</SheetTitle>
              {subtitle && <SheetDescription>{subtitle}</SheetDescription>}
            </div>
          </div>
        </SheetHeader>

        {/* Action Buttons List */}
        {actions.length > 0 && (
          <div className="py-3 space-y-2">
            {actions.map((act, i) => {
              const isDestructive = act.variant === 'danger' || act.variant === 'destructive';
              const isSuccess = act.variant === 'success';
              const isBrand = act.variant === 'brand';

              return (
                <button
                  key={i}
                  type="button"
                  onClick={() => {
                    onClose();
                    act.onClick();
                  }}
                  className={`w-full flex items-center justify-between p-3 rounded-2xl border transition-all duration-150 active:scale-[0.98] text-left cursor-pointer group ${
                    isDestructive
                      ? 'border-destructive/20 bg-destructive/10 text-destructive hover:bg-destructive/15'
                      : isSuccess
                      ? 'border-emerald-500/20 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/15'
                      : isBrand
                      ? 'border-brand-500/25 bg-brand-500/10 text-brand-600 dark:text-brand-400 hover:bg-brand-500/15'
                      : 'border-border/70 bg-card/60 hover:bg-accent hover:text-accent-foreground text-foreground'
                  }`}>
                  <div className="flex items-center gap-3 truncate">
                    <span className="flex items-center justify-center w-8 h-8 rounded-xl bg-background/80 border border-border/50 shrink-0 group-hover:scale-105 transition-transform">
                      {renderActionIcon(act.icon)}
                    </span>
                    <div className="flex flex-col truncate">
                      <span className="text-xs font-semibold tracking-tight">{act.label}</span>
                      {act.description && (
                        <span className="text-[10px] text-muted-foreground font-normal">
                          {act.description}
                        </span>
                      )}
                    </div>
                  </div>
                  <IconArrowUpRight className="w-4 h-4 opacity-50 group-hover:opacity-100 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition-all shrink-0 ml-2" />
                </button>
              );
            })}
          </div>
        )}

        {/* Custom Body Content */}
        {children && <div className="py-2 overflow-y-auto max-h-[60vh] flex-1">{children}</div>}
      </SheetContent>
    </Sheet>
  );
}

export default BottomSheet;
