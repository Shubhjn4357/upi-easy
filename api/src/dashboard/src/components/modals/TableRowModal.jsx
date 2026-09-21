// Database Table Row Edit & Insert Modal
import React from 'react';
import { Modal } from '../Modal.jsx';
import { Button } from '../ui/button.jsx';
import { Input, Label } from '../ui/input.jsx';

export function TableRowModal({
  isOpen,
  onClose,
  selectedTable,
  columns = [],
  initialRow = null,
  onSubmitRow,
}) {
  const isEditing = Boolean(initialRow);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isEditing ? `Edit ${selectedTable} Row` : `Insert in ${selectedTable}`}
      subtitle={isEditing ? "Update column values" : "New row entry"}>
      <form onSubmit={onSubmitRow} className="space-y-3 text-xs max-h-[60vh] overflow-y-auto pr-1">
        {columns.map((col) => (
          <div key={col.name} className="space-y-1">
            <Label className="font-mono">
              {col.name} {col.isPrimary && <span className="text-amber-500 font-bold">(PRIMARY)</span>}
              {!col.isPrimary && <span className="text-muted-foreground font-normal"> ({col.type})</span>}
            </Label>
            <Input
              type="text"
              name={col.name}
              defaultValue={initialRow ? initialRow[col.name] ?? "" : ""}
              disabled={isEditing && col.isPrimary}
              placeholder={!isEditing && col.isPrimary ? "Auto-generated if empty" : ""}
              className={`font-mono ${isEditing && col.isPrimary ? 'opacity-60 cursor-not-allowed' : ''}`}
            />
          </div>
        ))}
        <div className="pt-3 flex justify-end gap-2">
          <Button
            variant="outline"
            type="button"
            onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="brand"
            type="submit"
            className={!isEditing ? "bg-amber-600 hover:bg-amber-500 shadow-amber-500/20" : ""}>
            {isEditing ? "Save Changes" : "Insert"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default TableRowModal;
