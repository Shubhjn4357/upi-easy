import React from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardTitle, CardDescription } from '@/components/ui/card';
import { IconHeartPulse, IconActivity, IconRefreshCw } from '@/components/ui/icons';
import type { HealthData, HealthPageProps } from '@/types';

// System Telemetry & Health Page using shadcn/ui with strict TypeScript types
export function HealthPage({ healthData, pingMs, onRefresh, onResetSeed }: HealthPageProps) {
  return (
    <div className="space-y-6 animate-fade-in max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight text-foreground">
            System Telemetry & Health
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Process telemetry, SQLite database status, memory allocation, and latency
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={onRefresh}
            className="rounded-xl gap-1.5 font-semibold">
            <IconRefreshCw className="w-3.5 h-3.5" />
            <span>Refresh Metrics</span>
          </Button>
          <Button
            variant="brand"
            size="sm"
            onClick={onResetSeed}
            className="rounded-xl font-bold">
            Re-seed System Baseline
          </Button>
        </div>
      </div>

      {/* Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="p-5">
          <CardDescription className="text-xs font-bold uppercase tracking-wider">
            Gateway Status
          </CardDescription>
          <div className="text-2xl font-extrabold text-emerald-600 dark:text-emerald-400 mt-2 flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse"></span>
            {healthData?.status?.toUpperCase() || 'ONLINE'}
          </div>
          <div className="text-xs text-muted-foreground mt-2 font-mono">
            Uptime: {Math.floor((healthData?.uptimeSeconds || 0) / 60)} mins
          </div>
        </Card>

        <Card className="p-5">
          <CardDescription className="text-xs font-bold uppercase tracking-wider">
            Memory Allocation
          </CardDescription>
          <div className="text-2xl font-extrabold text-foreground mt-2">
            {healthData?.memory?.rssMb || '0'} <span className="text-xs font-normal text-muted-foreground">MB RSS</span>
          </div>
          <div className="text-xs text-muted-foreground mt-2 font-mono">
            Heap: {healthData?.memory?.heapUsedMb || '0'} / {healthData?.memory?.heapTotalMb || '0'} MB
          </div>
        </Card>

        <Card className="p-5">
          <CardDescription className="text-xs font-bold uppercase tracking-wider">
            API Roundtrip Latency
          </CardDescription>
          <div className="text-2xl font-extrabold text-brand-600 dark:text-cyan-400 mt-2 font-mono">
            {pingMs ? `${pingMs}ms` : '1ms'}
          </div>
          <div className="text-xs text-emerald-600 dark:text-emerald-400 mt-2">
            Local SQLite query bus
          </div>
        </Card>

        <Card className="p-5">
          <CardDescription className="text-xs font-bold uppercase tracking-wider">
            Runtime Platform
          </CardDescription>
          <div className="text-2xl font-extrabold text-indigo-600 dark:text-indigo-400 mt-2 truncate">
            {healthData?.nodeVersion || 'Node.js'}
          </div>
          <div className="text-xs text-muted-foreground mt-2 font-mono">
            Platform: {healthData?.platform || 'win32'}
          </div>
        </Card>
      </div>

      {/* Table Storage Breakdown */}
      <Card className="p-6 space-y-4">
        <CardTitle className="text-base font-bold">Database Table Storage Records</CardTitle>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
          {Object.entries(healthData?.tables || {}).map(([table, count]) => (
            <div key={table} className="p-3.5 rounded-xl bg-muted/40 border border-border">
              <div className="text-[11px] text-muted-foreground truncate font-mono">{table}</div>
              <div className="text-lg font-bold text-foreground mt-1 font-mono">{count}</div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}

export default HealthPage;
