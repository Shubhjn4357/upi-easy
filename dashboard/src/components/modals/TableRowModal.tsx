import React from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import Modal from '@/components/Modal';
import { IconDatabase } from '@/components/ui/icons';
import type { TableColumnDef } from '@/types';

export interface TableRowModalProps {
  isOpen: boolean;
  onClose: () => void;
  selectedTable: string;
  columns?: TableColumnDef[];
  initialRow?: Record<string, unknown> | null;
  onSubmitRow: (e: React.FormEvent<HTMLFormElement>) => void | Promise<void>;
}

// Database Table Row Edit & Insert Modal with strict TypeScript types
export function TableRowModal({
  isOpen,
  onClose,
  selectedTable,
  columns = [],
  initialRow = null,
  onSubmitRow,
}: TableRowModalProps) {
  const isEditing = Boolean(initialRow);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isEditing ? `Edit ${selectedTable} Row` : `Insert in ${selectedTable}`}
      subtitle={isEditing ? 'Update column values' : 'New database record entry'}
      icon={<IconDatabase className="w-4 h-4" />}>
      <form onSubmit={onSubmitRow} className="space-y-3.5 text-xs max-h-[60vh] overflow-y-auto pr-1">
        {columns.map((col) => (
          <Input
            key={col.name}
            label={`${col.name}${col.isPrimary ? ' (PRIMARY)' : ` (${col.type})`}`}
            type="text"
            name={col.name}
            defaultValue={initialRow ? String(initialRow[col.name] ?? '') : ''}
            disabled={isEditing && col.isPrimary}
            placeholder={!isEditing && col.isPrimary ? 'Auto-generated ID' : ''}
            className={`font-mono text-xs ${isEditing && col.isPrimary ? 'opacity-60 cursor-not-allowed bg-muted/30' : ''}`}
          />
        ))}

        <div className="pt-3 flex items-center justify-end gap-2.5 border-t border-border/50">
          <Button
            variant="outline"
            type="button"
            onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="brand"
            type="submit">
            {isEditing ? 'Save Changes' : 'Insert Record'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default TableRowModal;
