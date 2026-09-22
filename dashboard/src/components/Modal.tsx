import React from 'react';
import { Dialog, DialogContent, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { IconX } from '@/components/ui/icons';

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  maxWidth?: string;
}

// shadcn/ui Modal Shell using Dialog with strict TypeScript types
export function Modal({
  isOpen,
  onClose,
  title,
  subtitle,
  children,
  maxWidth = 'max-w-xl',
}: ModalProps) {
  if (!isOpen) return null;
  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className={`w-full ${maxWidth} max-h-[90vh] flex flex-col p-0 overflow-hidden`}>
        <div className="px-6 py-4 border-b border-border flex items-center justify-between">
          <div>
            <DialogTitle>{title}</DialogTitle>
            {subtitle && <DialogDescription>{subtitle}</DialogDescription>}
          </div>
          <Button
            variant="ghost"
            size="icon"
            onClick={onClose}
            className="h-8 w-8 rounded-lg text-muted-foreground hover:text-foreground">
            <IconX className="w-4 h-4" />
          </Button>
        </div>
        <div className="p-6 overflow-y-auto space-y-4">{children}</div>
      </DialogContent>
    </Dialog>
  );
}

export default Modal;
