// shadcn/ui Action Bottom Sheet / Drawer Component
import React from 'react';
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from './ui/sheet.jsx';
import { Button } from './ui/button.jsx';
import { IconX, IconArrowUpRight } from './ui/icons.jsx';

function renderActionIcon(icon) {
  if (!icon) return null;
  if (React.isValidElement(icon)) return icon;
  if (typeof icon === 'function') return React.createElement(icon, { className: "w-4 h-4 text-muted-foreground" });
  return <span className="text-sm">{icon}</span>;
}

function BottomSheet({ isOpen, onClose, title, subtitle, actions = [], children }) {
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
                  act.variant === "danger"
                    ? "destructive"
                    : act.variant === "success"
                    ? "outline"
                    : "secondary"
                }
                onClick={() => {
                  onClose();
                  act.onClick();
                }}
                className={`w-full justify-between h-10 px-4 rounded-xl ${
                  act.variant === "success"
                    ? "border-emerald-500/20 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/20"
                    : ""
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
        {children && (
          <div className="py-2 overflow-y-auto max-h-[60vh]">{children}</div>
        )}
      </SheetContent>
    </Sheet>
  );
}

export { BottomSheet };
export default BottomSheet;
