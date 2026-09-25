import React, { useEffect } from 'react';
import { Dialog, DialogContent, DialogTitle, DialogDescription, type DialogSize } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { IconX } from '@/components/ui/icons';

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
  children: React.ReactNode;
  footer?: React.ReactNode;
  maxWidth?: string;
  size?: DialogSize;
}

// Premium Glassmorphic Modal with smooth spring transition and rich header
export function Modal({
  isOpen,
  onClose,
  title,
  subtitle,
  icon,
  children,
  footer,
  maxWidth,
  size = 'md',
}: ModalProps) {
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    }
    if (isOpen) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent
        size={size}
        className={`w-full ${maxWidth || ''} max-h-[92vh] flex flex-col p-0 overflow-hidden shadow-2xl border-border/70`}>
        {/* Header */}
        <div className="px-6 py-4 border-b border-border/60 flex items-center justify-between bg-card/50 backdrop-blur-sm shrink-0">
          <div className="flex items-center gap-3">
            {icon && (
              <div className="w-9 h-9 rounded-2xl bg-primary/10 border border-primary/20 flex items-center justify-center text-primary shrink-0 shadow-inner">
                {icon}
              </div>
            )}
            <div>
              <DialogTitle className="text-base font-bold text-foreground tracking-tight">
                {title}
              </DialogTitle>
              {subtitle && (
                <DialogDescription className="text-xs text-muted-foreground mt-0.5">
                  {subtitle}
                </DialogDescription>
              )}
            </div>
          </div>

          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            className="h-8 w-8 rounded-full text-muted-foreground hover:text-foreground hover:bg-muted/80 transition-transform active:scale-90">
            <IconX className="w-4 h-4" />
          </Button>
        </div>

        {/* Scrollable Body */}
        <div className="p-6 overflow-y-auto space-y-4 flex-1">{children}</div>

        {/* Optional Footer */}
        {footer && (
          <div className="px-6 py-3.5 border-t border-border/60 bg-muted/20 flex items-center justify-end gap-2.5 shrink-0">
            {footer}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

export default Modal;
