import React from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { Avatar, AvatarFallback } from '@/components/ui/components';
import {
  IconPlus,
  IconMoreVertical,
  IconUserCheck,
  IconShield,
  IconUsers,
  IconCreditCard,
  IconActivity,
} from '@/components/ui/icons';
import { StaffPageSkeleton } from '@/components/ui/Skeleton';
import type { StaffMember, StaffInvite, StaffPageProps } from '@/types';

// Staff & Team Management Page using shadcn/ui with strict TypeScript types
export function StaffPage({
  staffList,
  invitesList,
  canManageStaff = true,
  loading = false,
  onOpenInviteStaff,
  onSelectStaffAction,
}: StaffPageProps) {
  if (loading) {
    return <StaffPageSkeleton />;
  }

  return (
    <div className="space-y-6 animate-fade-in max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight text-foreground">
            Staff & Team Access
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Manage store cashiers, managers, role permissions and account statuses
          </p>
        </div>
        {canManageStaff && (
          <Button
            variant="brand"
            size="sm"
            onClick={onOpenInviteStaff}
            className="rounded-xl gap-1.5 font-bold self-start sm:self-auto">
            <IconPlus className="w-3.5 h-3.5" />
            <span>Add Team Member</span>
          </Button>
        )}
      </div>

        {/* Staff Table */}
          <Card className="overflow-hidden">
            <div className="px-6 py-4 border-b border-border flex items-center justify-between">
              <CardTitle className="text-sm font-bold">Active Members ({staffList.length})</CardTitle>
            </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Member</TableHead>
              <TableHead>Mobile / Email</TableHead>
              <TableHead>Assigned Role</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Joined At</TableHead>
              <TableHead className="text-right">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {staffList.map((member: any) => (
              <TableRow key={member.id}>
                <TableCell className="font-medium text-foreground">
                  <div className="flex items-center gap-3">
                    <Avatar className="w-8 h-8">
                      <AvatarFallback className="bg-brand-500/10 text-brand-600 dark:text-cyan-400">
                        {(member.fullName || member.name || 'U').slice(0, 1).toUpperCase()}
                      </AvatarFallback>
                    </Avatar>
                    <span>{member.fullName || member.name || 'Store Staff'}</span>
                  </div>
                </TableCell>
                <TableCell className="text-muted-foreground font-mono">
                  <div>{member.mobileNumber || '—'}</div>
                  <div className="text-[10px] text-muted-foreground/80">{member.email || ''}</div>
                </TableCell>
                <TableCell>
                  <Badge
                    variant={
                      member.role === 'OWNER'
                        ? 'warning'
                        : member.role === 'MANAGER'
                        ? 'secondary'
                        : member.role === 'CASHIER'
                        ? 'success'
                        : 'outline'
                    }
                    className="uppercase tracking-wide text-[10px]">
                    {member.role || 'MEMBER'}
                  </Badge>
                </TableCell>
                <TableCell>
                  <Badge
                    variant={member.status === 'ACTIVE' ? 'success' : 'destructive'}
                    className="text-[10px]">
                    {member.status || 'ACTIVE'}
                  </Badge>
                </TableCell>
                <TableCell className="text-muted-foreground text-[11px]">
                  {member.joinedAt ? new Date(member.joinedAt).toLocaleDateString() : 'Active'}
                </TableCell>
                <TableCell className="text-right">
                  {canManageStaff ? (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => onSelectStaffAction('manage', member)}
                      className="rounded-lg h-7 px-2.5 text-xs gap-1">
                      
                      <IconMoreVertical className="w-3 h-3" />
                    </Button>
                  ) : (
                    <span className="text-xs text-muted-foreground">—</span>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>

      {/* Role Capabilities Reference Card */}
      <Card className="p-5">
        <CardTitle className="text-sm font-bold mb-1">Role Permissions Matrix</CardTitle>
        <CardDescription className="mb-4">Permissions assigned across standard organizational roles</CardDescription>
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3 text-xs">
          <div className="p-3.5 rounded-xl bg-muted/40 border border-border">
            <div className="font-bold text-amber-500 dark:text-amber-400 flex items-center gap-1.5">
              <IconShield className="w-3.5 h-3.5" /> OWNER
            </div>
            <div className="text-muted-foreground mt-1.5">Full control, manage settlements, audit logs, and assign permissions.</div>
          </div>
          <div className="p-3.5 rounded-xl bg-muted/40 border border-border">
            <div className="font-bold text-indigo-500 dark:text-indigo-400 flex items-center gap-1.5">
              <IconUsers className="w-3.5 h-3.5" /> MANAGER
            </div>
            <div className="text-muted-foreground mt-1.5">Staff management, UPI accounts, initiate refunds, view reports.</div>
          </div>
          <div className="p-3.5 rounded-xl bg-muted/40 border border-border">
            <div className="font-bold text-emerald-500 dark:text-emerald-400 flex items-center gap-1.5">
              <IconCreditCard className="w-3.5 h-3.5" /> CASHIER
            </div>
            <div className="text-muted-foreground mt-1.5">Generate dynamic QR codes, record customer payments, verify receipts.</div>
          </div>
          <div className="p-3.5 rounded-xl bg-muted/40 border border-border">
            <div className="font-bold text-cyan-500 dark:text-cyan-400 flex items-center gap-1.5">
              <IconActivity className="w-3.5 h-3.5" /> ACCOUNTANT
            </div>
            <div className="text-muted-foreground mt-1.5">Read-only transaction ledger access, export statements to CSV.</div>
          </div>
        </div>
      </Card>
    </div>
  );
}

export default StaffPage;
