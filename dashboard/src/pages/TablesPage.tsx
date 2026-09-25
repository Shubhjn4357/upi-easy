import React, { useMemo, useState } from 'react';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import useConfirmDialog from '@/hooks/useConfirmDialog';
import { DataTable, type DataTableColumn } from '@/components/ui/DataTable';
import {
  IconPlus,
  IconMoreVertical,
  IconTrash,
  IconChevronLeft,
  IconChevronRight,
} from '@/components/ui/icons';
import type { TablesPageProps, TableColumnDef } from '@/types';

export function TablesPage({
  tables,
  selectedTable,
  onSelectTable,
  tableData,
  loading,
  search,
  onSearchChange,
  offset = 0,
  onOffsetChange,
  limit = 25,
  onOpenInsertModal,
  onSelectRowAction,
}: TablesPageProps) {
  const [selectedRowIds, setSelectedRowIds] = useState<Set<string>>(new Set());
  const { confirm, dialogState, handleConfirm, handleCancel } = useConfirmDialog();

  // Infer columns from rows if schema columns not populated
  const displayColumns: TableColumnDef[] = useMemo(() => {
    if (tableData?.columns && tableData.columns.length > 0) {
      return tableData.columns;
    }
    if (tableData?.rows && tableData.rows.length > 0) {
      return Object.keys(tableData.rows[0]).map((key) => ({
        name: key,
        type: 'TEXT',
        isPrimary: key === 'id',
      }));
    }
    return [];
  }, [tableData]);

  const total = tableData?.total || 0;
  const currentPage = Math.floor(offset / limit) + 1;
  const totalPages = Math.max(1, Math.ceil(total / limit));
  const hasPrev = offset > 0;
  const hasNext = offset + limit < total;

  const pkCol = useMemo(() => {
    return displayColumns.find((c) => c.isPrimary)?.name || 'id';
  }, [displayColumns]);

  const keyExtractor = (row: Record<string, unknown>) => {
    return String(row[pkCol] ?? Math.random());
  };

  const columns = useMemo(() => {
    const cols: DataTableColumn<Record<string, unknown>>[] = displayColumns.map((col) => ({
      header: col.name,
      cell: (row: Record<string, unknown>) => {
        const val = row[col.name];
        if (val === null || val === undefined) {
          return <span className="text-muted-foreground/50 italic text-[11px]">NULL</span>;
        }
        if (typeof val === 'boolean') {
          return (
            <span
              className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                val
                  ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                  : 'bg-rose-500/10 text-rose-600 dark:text-rose-400'
              }`}>
              {val ? 'TRUE' : 'FALSE'}
            </span>
          );
        }
        if (typeof val === 'object') {
          return (
            <span className="font-mono text-[10px] text-muted-foreground truncate max-w-[200px] block">
              {JSON.stringify(val)}
            </span>
          );
        }
        return (
          <span className={`text-xs ${col.isPrimary ? 'font-mono font-bold text-foreground' : 'text-foreground/90'}`}>
            {String(val)}
          </span>
        );
      },
    }));

    cols.push({
      header: 'Action',
      headerClassName: 'text-right',
      cellClassName: 'text-right',
      cell: (row: Record<string, unknown>) => (
        <Button
          variant="outline"
          size="sm"
          onClick={() => onSelectRowAction(row, selectedTable, displayColumns)}
          className="rounded-lg h-7 px-2 text-xs">
          <IconMoreVertical className="w-3 h-3" />
        </Button>
      ),
    });

    return cols;
  }, [displayColumns, onSelectRowAction, selectedTable]);

  // Options for the top table selector dropdown menu
  const tableDropdownOptions = tables.map((t) => ({
    value: t.name,
    label: t.name,
    count: t.rowCount,
  }));

  return (
    <div className="space-y-5 animate-fade-in max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight text-foreground">
            Database Table Explorer
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Direct schema inspection, record insertion, editing, and bulk deletion
          </p>
        </div>
        <Button
          variant="brand"
          size="sm"
          onClick={onOpenInsertModal}
          className="rounded-xl gap-1.5 font-bold self-start sm:self-auto bg-amber-600 hover:bg-amber-500 shadow-amber-500/20">
          <IconPlus className="w-3.5 h-3.5" />
          <span>Insert in {selectedTable}</span>
        </Button>
      </div>

      {/* Modular DataTable with Table Dropdown Menu & Bulk Actions */}
      <DataTable
        data={tableData?.rows || []}
        keyExtractor={keyExtractor}
        columns={columns}
        loading={loading}
        emptyMessage={`No records found in table "${selectedTable}".`}
        search={search}
        onSearchChange={onSearchChange}
        searchPlaceholder={`Search rows in ${selectedTable}...`}
        menuDropdowns={[
          {
            id: 'table',
            label: 'Table',
            value: selectedTable,
            options: tableDropdownOptions,
            onChange: (newTable) => {
              onSelectTable(newTable);
              setSelectedRowIds(new Set());
            },
          },
        ]}
        enableBulkSelect={true}
        selectedIds={selectedRowIds}
        onSelectionChange={setSelectedRowIds}
        bulkActions={[
          {
            label: 'Delete Selected Rows',
            icon: <IconTrash className="w-3.5 h-3.5" />,
            variant: 'destructive',
            onClick: async (ids: string[]) => {
              const ok = await confirm({
                title: 'Delete Selected Database Rows',
                description: `Are you sure you want to delete ${ids.length} selected row(s) from "${selectedTable}"? This action directly removes records from the database table.`,
                variant: 'danger',
                confirmText: `Delete (${ids.length})`,
              });
              if (!ok) return;
              // Row deletion action
              setSelectedRowIds(new Set());
            },
          },
        ]}
      />

      {/* Pagination Bar */}
      <div className="flex items-center justify-between text-xs text-muted-foreground px-2">
        <div>
          Showing {total === 0 ? 0 : offset + 1} to {Math.min(offset + limit, total)} of {total} records
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={!hasPrev || loading}
            onClick={() => onOffsetChange?.(Math.max(0, offset - limit))}
            className="rounded-xl h-8 px-3 gap-1">
            <IconChevronLeft className="w-3.5 h-3.5" />
            <span>Prev</span>
          </Button>
          <span className="font-medium text-foreground px-1">
            Page {currentPage} of {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={!hasNext || loading}
            onClick={() => onOffsetChange?.(offset + limit)}
            className="rounded-xl h-8 px-3 gap-1">
            <span>Next</span>
            <IconChevronRight className="w-3.5 h-3.5" />
          </Button>
        </div>
      </div>

      {/* Custom Confirm Dialog */}
      {dialogState && (
        <ConfirmDialog
          open={dialogState.open}
          onOpenChange={(open) => !open && handleCancel()}
          title={dialogState.title}
          description={dialogState.description}
          variant={dialogState.variant}
          confirmText={dialogState.confirmText}
          cancelText={dialogState.cancelText}
          onConfirm={handleConfirm}
          onCancel={handleCancel}
        />
      )}
    </div>
  );
}

export default TablesPage;
