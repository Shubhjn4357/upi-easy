import React from 'react';

export interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
}

export function Skeleton({ className = '', ...props }: SkeletonProps) {
  return (
    <div
      className={`animate-pulse rounded-xl bg-muted/60 dark:bg-muted/30 relative overflow-hidden before:absolute before:inset-0 before:-translate-x-full before:animate-[shimmer_1.8s_infinite] before:bg-gradient-to-r before:from-transparent before:via-white/10 before:to-transparent ${className}`}
      {...props}
    />
  );
}

export function BentoOverviewSkeleton() {
  return (
    <div className="space-y-6 animate-fade-in max-w-7xl mx-auto">
      {/* Top Banner Skeleton */}
      <Skeleton className="h-40 w-full rounded-3xl" />

      {/* Stats Bento Grid Skeleton */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {[1, 2, 3, 4].map((i) => (
          <Skeleton key={i} className="h-28 rounded-2xl" />
        ))}
      </div>

      {/* Main Content 2-Column Split */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-4">
          <Skeleton className="h-72 rounded-3xl" />
          <Skeleton className="h-48 rounded-2xl" />
        </div>
        <div className="space-y-4">
          <Skeleton className="h-64 rounded-3xl" />
          <Skeleton className="h-56 rounded-3xl" />
        </div>
      </div>
    </div>
  );
}

export function TransactionsPageSkeleton() {
  return (
    <div className="space-y-5 animate-fade-in max-w-7xl mx-auto">
      <div className="flex justify-between items-center">
        <Skeleton className="h-8 w-48 rounded-xl" />
        <Skeleton className="h-10 w-32 rounded-xl" />
      </div>
      <div className="flex gap-2">
        <Skeleton className="h-10 flex-1 rounded-xl" />
        <Skeleton className="h-10 w-28 rounded-xl" />
        <Skeleton className="h-10 w-28 rounded-xl" />
      </div>
      <div className="space-y-3">
        {[1, 2, 3, 4, 5, 6].map((i) => (
          <div key={i} className="p-4 rounded-2xl border border-border/50 bg-card/40 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Skeleton className="w-10 h-10 rounded-full" />
              <div className="space-y-1.5">
                <Skeleton className="w-36 h-4 rounded-md" />
                <Skeleton className="w-24 h-3 rounded-md" />
              </div>
            </div>
            <div className="space-y-1.5 flex flex-col items-end">
              <Skeleton className="w-20 h-4 rounded-md" />
              <Skeleton className="w-16 h-3 rounded-md" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export function AccountsPageSkeleton() {
  return (
    <div className="space-y-6 animate-fade-in max-w-7xl mx-auto">
      <div className="flex justify-between items-center">
        <Skeleton className="h-8 w-56 rounded-xl" />
        <Skeleton className="h-10 w-44 rounded-xl" />
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="p-6 rounded-2xl border border-border/50 bg-card/40 space-y-4">
            <div className="flex justify-between items-center">
              <Skeleton className="w-32 h-5 rounded-md" />
              <Skeleton className="w-16 h-6 rounded-full" />
            </div>
            <div className="p-4 rounded-xl bg-muted/20 space-y-2.5">
              <Skeleton className="w-full h-4 rounded-md" />
              <Skeleton className="w-full h-4 rounded-md" />
              <Skeleton className="w-full h-4 rounded-md" />
            </div>
            <div className="flex gap-2">
              <Skeleton className="h-9 flex-1 rounded-xl" />
              <Skeleton className="h-9 w-20 rounded-xl" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export function UpiPageSkeleton() {
  return (
    <div className="space-y-6 animate-fade-in max-w-7xl mx-auto">
      <div className="flex justify-between items-center">
        <Skeleton className="h-8 w-44 rounded-xl" />
        <Skeleton className="h-10 w-36 rounded-xl" />
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {[1, 2, 3].map((i) => (
          <div key={i} className="p-6 rounded-2xl border border-border/50 bg-card/40 space-y-4">
            <div className="flex items-center gap-3">
              <Skeleton className="w-12 h-12 rounded-2xl" />
              <div className="space-y-1.5 flex-1">
                <Skeleton className="w-28 h-4 rounded-md" />
                <Skeleton className="w-36 h-3 rounded-md" />
              </div>
            </div>
            <Skeleton className="w-full h-24 rounded-xl" />
            <div className="flex gap-2">
              <Skeleton className="h-9 flex-1 rounded-xl" />
              <Skeleton className="h-9 flex-1 rounded-xl" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export function StaffPageSkeleton() {
  return (
    <div className="space-y-6 animate-fade-in max-w-7xl mx-auto">
      <div className="flex justify-between items-center">
        <Skeleton className="h-8 w-44 rounded-xl" />
        <Skeleton className="h-10 w-36 rounded-xl" />
      </div>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-24 rounded-2xl" />
        ))}
      </div>
      <div className="space-y-3">
        {[1, 2, 3, 4].map((i) => (
          <Skeleton key={i} className="h-16 rounded-2xl" />
        ))}
      </div>
    </div>
  );
}

export default Skeleton;
