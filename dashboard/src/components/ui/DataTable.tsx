import React, { useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from '@/components/ui/table';
import {
  IconSearch,
  IconX,
  IconTrash,
  IconChevronDown,
} from '@/components/ui/icons';

export interface DropdownOption {
  value: string;
  label: string;
  count?: number;
}

export interface DataTableColumn<T> {
  header: string;
  headerClassName?: string;
  cell: (item: T) => React.ReactNode;
  cellClassName?: string;
}

export interface DataTableProps<T> {
  data: T[];
  keyExtractor: (item: T) => string;
  columns: DataTableColumn<T>[];
  loading?: boolean;
  emptyMessage?: string;

  // Search
  search?: string;
  onSearchChange?: (val: string) => void;
  searchPlaceholder?: string;

  // Menu-style dropdowns (replacing pills)
  menuDropdowns?: {
    id: string;
    label: string;
    value: string;
    options: DropdownOption[];
    onChange: (val: string) => void;
  }[];

  // Bulk Selection Actions
  enableBulkSelect?: boolean;
  selectedIds?: Set<string>;
  onSelectionChange?: (selectedIds: Set<string>) => void;
  bulkActions?: {
    label: string;
    icon?: React.ReactNode;
    variant?: 'brand' | 'destructive' | 'outline' | 'secondary';
    onClick: (selectedIds: string[]) => void | Promise<void>;
  }[];

  // Extra Header Actions (e.g. Export, Refresh, Add)
  headerActions?: React.ReactNode;
}

export function DataTable<T>({
  data,
  keyExtractor,
  columns,
  loading = false,
  emptyMessage = 'No records found.',
  search,
  onSearchChange,
  searchPlaceholder = 'Search records...',
  menuDropdowns = [],
  enableBulkSelect = false,
  selectedIds = new Set<string>(),
  onSelectionChange,
  bulkActions = [],
  headerActions,
}: DataTableProps<T>) {
  const allIds = data.map(keyExtractor);
  const isAllSelected = allIds.length > 0 && allIds.every((id) => selectedIds.has(id));
  const isSomeSelected = selectedIds.size > 0 && !isAllSelected;

  const toggleSelectAll = () => {
    if (!onSelectionChange) return;
    if (isAllSelected) {
      onSelectionChange(new Set());
    } else {
      onSelectionChange(new Set(allIds));
    }
  };

  const toggleSelectItem = (id: string) => {
    if (!onSelectionChange) return;
    const next = new Set(selectedIds);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    onSelectionChange(next);
  };

  return (
    <div className="space-y-3">
      {/* Top Filter & Menu Action Bar */}
      <Card className="p-3 flex flex-col md:flex-row items-center justify-between gap-3 bg-card/80 backdrop-blur-sm border-border">
        {/* Left side: Search & Menu-style dropdown filters */}
        <div className="flex flex-1 flex-wrap items-center gap-2.5 w-full">
          {onSearchChange !== undefined && (
            <div className="relative flex-1 min-w-[200px] max-w-md w-full">
              <IconSearch className="absolute left-3 top-2.5 w-4 h-4 text-muted-foreground pointer-events-none" />
              <Input
                type="text"
                placeholder={searchPlaceholder}
                value={search || ''}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => onSearchChange(e.target.value)}
                className="pl-9 pr-8 h-9 text-xs rounded-xl"
              />
              {search && (
                <button
                  type="button"
                  onClick={() => onSearchChange('')}
                  className="absolute right-2.5 top-2.5 text-muted-foreground hover:text-foreground">
                  <IconX className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          )}

          {/* Menu-style dropdowns (Clean modern alternative to raw horizontal pills) */}
          {menuDropdowns.map((menu) => (
            <div key={menu.id} className="relative inline-flex items-center">
              <select
                value={menu.value}
                onChange={(e) => menu.onChange(e.target.value)}
                className="appearance-none h-9 pl-3 pr-8 bg-background border border-input rounded-xl text-xs font-medium text-foreground hover:border-primary/50 focus:outline-none focus:ring-1 focus:ring-ring transition shadow-sm cursor-pointer">
                {menu.options.map((opt) => (
                  <option key={opt.value} value={opt.value} className="bg-card text-foreground">
                    {opt.label} {opt.count !== undefined ? `(${opt.count})` : ''}
                  </option>
                ))}
              </select>
              <IconChevronDown className="absolute right-2.5 pointer-events-none w-3.5 h-3.5 text-muted-foreground" />
            </div>
          ))}
        </div>

        {/* Right side: Global Header Actions */}
        {headerActions && (
          <div className="flex items-center gap-2 self-end md:self-auto shrink-0">
            {headerActions}
          </div>
        )}
      </Card>

      {/* Floating Bulk Action Bar (when rows are selected) */}
      {enableBulkSelect && selectedIds.size > 0 && (
        <div className="flex items-center justify-between p-3 rounded-2xl bg-primary/10 border border-primary/20 backdrop-blur-md animate-fade-in shadow-md">
          <div className="flex items-center gap-2.5">
            <span className="flex items-center justify-center w-6 h-6 rounded-lg bg-primary text-primary-foreground text-xs font-bold">
              {selectedIds.size}
            </span>
            <span className="text-xs font-bold text-foreground">
              {selectedIds.size} {selectedIds.size === 1 ? 'item' : 'items'} selected
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => onSelectionChange?.(new Set())}
              className="h-7 px-2 text-[11px] rounded-lg">
              Deselect All
            </Button>
          </div>

          <div className="flex items-center gap-2">
            {bulkActions.map((action, idx) => (
              <Button
                key={idx}
                variant={action.variant || 'destructive'}
                size="sm"
                onClick={() => action.onClick(Array.from(selectedIds))}
                className="h-7 px-3 text-xs rounded-xl gap-1.5 font-bold shadow-sm">
                {action.icon || <IconTrash className="w-3.5 h-3.5" />}
                <span>{action.label}</span>
              </Button>
            ))}
          </div>
        </div>
      )}

      {/* Table Canvas */}
      <Card className="overflow-hidden border border-border bg-card/90">
        <Table>
          <TableHeader>
            <TableRow>
              {enableBulkSelect && (
                <TableHead className="w-10 px-3">
                  <input
                    type="checkbox"
                    checked={isAllSelected}
                    ref={(el) => {
                      if (el) el.indeterminate = isSomeSelected;
                    }}
                    onChange={toggleSelectAll}
                    className="rounded border-border text-primary focus:ring-primary w-4 h-4 cursor-pointer"
                  />
                </TableHead>
              )}
              {columns.map((col, idx) => (
                <TableHead key={idx} className={col.headerClassName}>
                  {col.header}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell
                  colSpan={columns.length + (enableBulkSelect ? 1 : 0)}
                  className="py-10 text-center text-muted-foreground">
                  <div className="flex items-center justify-center gap-2">
                    <div className="w-4 h-4 rounded-full border-2 border-primary border-t-transparent animate-spin" />
                    <span>Loading data...</span>
                  </div>
                </TableCell>
              </TableRow>
            ) : data.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={columns.length + (enableBulkSelect ? 1 : 0)}
                  className="py-10 text-center text-muted-foreground text-xs">
                  {emptyMessage}
                </TableCell>
              </TableRow>
            ) : (
              data.map((item) => {
                const id = keyExtractor(item);
                const isSelected = selectedIds.has(id);
                return (
                  <TableRow
                    key={id}
                    data-state={isSelected ? 'selected' : undefined}
                    className={`transition-colors ${
                      isSelected ? 'bg-primary/5 hover:bg-primary/10' : ''
                    }`}>
                    {enableBulkSelect && (
                      <TableCell className="w-10 px-3">
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => toggleSelectItem(id)}
                          className="rounded border-border text-primary focus:ring-primary w-4 h-4 cursor-pointer"
                        />
                      </TableCell>
                    )}
                    {columns.map((col, idx) => (
                      <TableCell key={idx} className={col.cellClassName}>
                        {col.cell(item)}
                      </TableCell>
                    ))}
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </Card>
    </div>
  );
}

export default DataTable;
