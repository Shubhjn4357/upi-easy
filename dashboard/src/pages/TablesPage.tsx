import React from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { IconPlus, IconSearch, IconMoreVertical } from '@/components/ui/icons';

export interface ColumnDef {
  name: string;
  type?: string;
  isPrimary?: boolean;
}

export interface TableItem {
  name: string;
  rowCount?: number;
}

export interface TablesPageProps {
  tables: any[];
  selectedTable: string;
  onSelectTable: (tableName: string) => void;
  tableData: {
    rows: Record<string, any>[];
    columns: ColumnDef[];
    total: number;
  };
  loading: boolean;
  search: string;
  onSearchChange: (search: string) => void;
  onOpenInsertModal: () => void;
  onSelectRowAction: (row: Record<string, any>, tableName: string, columns: ColumnDef[]) => void;
}

// Database Table Explorer & Management Page using shadcn/ui with strict TypeScript types
export function TablesPage({
  tables,
  selectedTable,
  onSelectTable,
  tableData,
  loading,
  search,
  onSearchChange,
  onOpenInsertModal,
  onSelectRowAction,
}: TablesPageProps) {
  return (
    <div className="space-y-6 animate-fade-in max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight text-foreground">
            Database Table Explorer
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Direct schema inspection, row insertion, editing, and record deletion
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

      {/* Table Selector Pills */}
      <div className="flex items-center gap-2 overflow-x-auto pb-2">
        {tables.map((t: any) => (
          <Button
            key={t.name}
            variant={selectedTable === t.name ? 'default' : 'secondary'}
            size="sm"
            onClick={() => onSelectTable(t.name)}
            className="rounded-xl whitespace-nowrap gap-2 font-mono text-xs">
            <span>{t.name}</span>
            <Badge variant="outline" className="px-1.5 py-0 text-[10px] font-mono">
              {t.rowCount ?? 0}
            </Badge>
          </Button>
        ))}
      </div>

      {/* Table Card */}
      <Card className="overflow-hidden">
        <div className="p-3.5 border-b border-border flex items-center justify-between gap-4">
          <div className="relative max-w-sm w-full">
            <IconSearch className="absolute left-3 top-2.5 w-4 h-4 text-muted-foreground pointer-events-none" />
            <Input
              type="text"
              placeholder={`Search ${selectedTable}...`}
              value={search}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => onSearchChange(e.target.value)}
              className="pl-9"
            />
          </div>
          <div className="text-xs text-muted-foreground font-mono">
            {tableData?.rows?.length || 0} of {tableData?.total || 0} rows
          </div>
        </div>

        <div className="overflow-x-auto max-h-[580px]">
          <Table>
            <TableHeader className="sticky top-0 backdrop-blur z-10">
              <TableRow>
                {(tableData?.columns || []).map((col) => (
                  <TableHead key={col.name} className="font-mono whitespace-nowrap">
                    <span
                      className={
                        col.isPrimary
                          ? 'text-amber-500 dark:text-amber-400 font-bold'
                          : 'text-foreground'
                      }>
                      {col.name}
                    </span>
                    {col.type && (
                      <span className="ml-1 text-[10px] text-muted-foreground font-normal">
                        ({col.type})
                      </span>
                    )}
                  </TableHead>
                ))}
                <TableHead className="text-right font-mono">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={20} className="py-8 text-center text-muted-foreground">
                    Loading {selectedTable}...
                  </TableCell>
                </TableRow>
              ) : (tableData?.rows || []).length === 0 ? (
                <TableRow>
                  <TableCell colSpan={20} className="py-8 text-center text-muted-foreground">
                    No records found.
                  </TableCell>
                </TableRow>
              ) : (
                tableData.rows.map((row: any, idx: number) => {
                  const pkCol = tableData.columns?.find((c) => c.isPrimary)?.name || 'id';
                  const pkVal = row[pkCol] ?? idx;
                  return (
                    <TableRow key={pkVal}>
                      {(tableData.columns || []).map((col) => {
                        const val = row[col.name];
                        return (
                          <TableCell
                            key={col.name}
                            className="font-mono max-w-xs truncate"
                            title={String(val)}>
                            {val === null || val === undefined ? (
                              <span className="text-muted-foreground/50 italic">null</span>
                            ) : (
                              String(val)
                            )}
                          </TableCell>
                        );
                      })}
                      <TableCell className="text-right whitespace-nowrap">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => onSelectRowAction(row, selectedTable, tableData.columns)}
                          className="rounded-lg h-7 px-2.5 text-[11px] font-semibold gap-1">
                          <span>Actions</span>
                          <IconMoreVertical className="w-3 h-3" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        </div>
      </Card>
    </div>
  );
}

export default TablesPage;
