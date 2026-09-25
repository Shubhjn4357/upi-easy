import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback } from '@/components/ui/components';
import { DataTable } from '@/components/ui/DataTable';
import {
  IconPlus,
  IconMoreVertical,
  IconShield,
  IconUsers,
  IconCreditCard,
  IconActivity,
  IconTrash,
} from '@/components/ui/icons';
import { StaffPageSkeleton } from '@/components/ui/Skeleton';
import type { StaffMember, StaffInvite, StaffPageProps } from '@/types';

export function StaffPage({
  staffList,
  invitesList,
  canManageStaff = true,
  loading = false,
  onOpenInviteStaff,
  onSelectStaffAction,
  onBulkDeleteStaff,
  onBulkDeleteInvites,
}: StaffPageProps) {
  const [selectedStaffIds, setSelectedStaffIds] = useState<Set<string>>(new Set());
  const [selectedInviteIds, setSelectedInviteIds] = useState<Set<string>>(new Set());
  const [staffSearch, setStaffSearch] = useState<string>('');
  const [roleFilter, setRoleFilter] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [activeSection, setActiveSection] = useState<'members' | 'invites'>('members');

  if (loading) {
    return <StaffPageSkeleton />;
  }

  // Filter staff members based on search, role, status
  const filteredStaff = staffList.filter((m) => {
    const matchesSearch =
      !staffSearch ||
      (m.fullName || m.name || '').toLowerCase().includes(staffSearch.toLowerCase()) ||
      (m.mobileNumber || '').includes(staffSearch) ||
      (m.email || '').toLowerCase().includes(staffSearch.toLowerCase());
    const matchesRole = !roleFilter || m.role.toUpperCase() === roleFilter.toUpperCase();
    const matchesStatus = !statusFilter || (m.status || 'ACTIVE').toUpperCase() === statusFilter.toUpperCase();
    return matchesSearch && matchesRole && matchesStatus;
  });

  const staffColumns = [
    {
      header: 'Member',
      cell: (member: StaffMember) => (
        <div className="flex items-center gap-3">
          <Avatar className="w-8 h-8">
            <AvatarFallback className="bg-brand-500/10 text-brand-600 dark:text-cyan-400">
              {(member.fullName || member.name || 'U').slice(0, 1).toUpperCase()}
            </AvatarFallback>
          </Avatar>
          <span className="font-medium text-foreground">{member.fullName || member.name || 'Store Staff'}</span>
        </div>
      ),
    },
    {
      header: 'Mobile / Email',
      cell: (member: StaffMember) => (
        <div className="font-mono text-muted-foreground text-xs">
          <div>{member.mobileNumber || '—'}</div>
          <div className="text-[10px] text-muted-foreground/80">{member.email || ''}</div>
        </div>
      ),
    },
    {
      header: 'Assigned Role',
      cell: (member: StaffMember) => (
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
      ),
    },
    {
      header: 'Status',
      cell: (member: StaffMember) => (
        <Badge
          variant={member.status === 'ACTIVE' ? 'success' : 'destructive'}
          className="text-[10px]">
          {member.status || 'ACTIVE'}
        </Badge>
      ),
    },
    {
      header: 'Joined At',
      cell: (member: StaffMember) => (
        <span className="text-muted-foreground text-[11px]">
          {member.joinedAt ? new Date(member.joinedAt).toLocaleDateString() : 'Active'}
        </span>
      ),
    },
    {
      header: 'Action',
      headerClassName: 'text-right',
      cellClassName: 'text-right',
      cell: (member: StaffMember) =>
        canManageStaff ? (
          <Button
            variant="outline"
            size="sm"
            onClick={() => onSelectStaffAction('manage', member)}
            className="rounded-lg h-7 px-2.5 text-xs gap-1">
            <IconMoreVertical className="w-3 h-3" />
          </Button>
        ) : (
          <span className="text-xs text-muted-foreground">—</span>
        ),
    },
  ];

  const inviteColumns = [
    {
      header: 'Invited Recipient',
      cell: (invite: StaffInvite) => {
        const targetEmail = invite.invitedEmail || invite.email;
        return (
          <div>
            <div className="font-mono text-xs text-foreground">{targetEmail}</div>
            {(invite.invitedName || invite.invitedMobile) && (
              <div className="text-[10px] text-muted-foreground mt-0.5">
                {[invite.invitedName, invite.invitedMobile].filter(Boolean).join(' · ')}
              </div>
            )}
          </div>
        );
      },
    },
    {
      header: 'Assigned Role',
      cell: (invite: StaffInvite) => (
        <Badge
          variant={
            invite.role === 'MANAGER'
              ? 'secondary'
              : invite.role === 'CASHIER'
              ? 'success'
              : 'outline'
          }
          className="uppercase tracking-wide text-[10px]">
          {invite.role || 'CASHIER'}
        </Badge>
      ),
    },
    {
      header: 'Status',
      cell: () => (
        <Badge variant="warning" className="text-[10px]">
          PENDING
        </Badge>
      ),
    },
    {
      header: 'Expires',
      cell: (invite: StaffInvite) => (
        <span className="text-muted-foreground text-[11px]">
          {invite.expiresAt ? new Date(invite.expiresAt).toLocaleDateString() : '7 days'}
        </span>
      ),
    },
    {
      header: 'Shareable Link',
      cell: (invite: StaffInvite) => {
        const inviteUrl = `${window.location.origin}/invite/${invite.token || invite.id}`;
        return (
          <Button
            variant="outline"
            size="sm"
            onClick={() => navigator.clipboard.writeText(inviteUrl)}
            className="rounded-lg h-7 px-2.5 text-xs font-mono">
            Copy Link
          </Button>
        );
      },
    },
    {
      header: 'Action',
      headerClassName: 'text-right',
      cellClassName: 'text-right',
      cell: (invite: StaffInvite) => (
        <Button
          variant="destructive"
          size="sm"
          onClick={() => onSelectStaffAction('revoke_invite', invite)}
          className="rounded-lg h-7 px-2.5 text-xs">
          Revoke
        </Button>
      ),
    },
  ];

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

      {/* Modern Top View / Section Dropdown Menu */}
      {/* Modern Top View / Section Dropdown Menu */}
      {activeSection === 'members' ? (
        <DataTable<StaffMember>
          data={filteredStaff}
          keyExtractor={(item) => item.id}
          columns={staffColumns}
          loading={false}
          emptyMessage="No team members match your criteria."
          search={staffSearch}
          onSearchChange={setStaffSearch}
          searchPlaceholder="Search by member name, phone or email..."
          menuDropdowns={[
            {
              id: 'section',
              label: 'View',
              value: activeSection,
              options: [
                { value: 'members', label: 'Active Members', count: staffList.length },
                { value: 'invites', label: 'Pending Invitations', count: invitesList.length },
              ],
              onChange: (val) => {
                setActiveSection(val as 'members' | 'invites');
                setSelectedStaffIds(new Set());
                setSelectedInviteIds(new Set());
              },
            },
            {
              id: 'role',
              label: 'Role',
              value: roleFilter,
              options: [
                { value: '', label: 'All Roles' },
                { value: 'OWNER', label: 'Owner' },
                { value: 'MANAGER', label: 'Manager' },
                { value: 'CASHIER', label: 'Cashier' },
              ],
              onChange: setRoleFilter,
            },
            {
              id: 'status',
              label: 'Status',
              value: statusFilter,
              options: [
                { value: '', label: 'All Statuses' },
                { value: 'ACTIVE', label: 'Active' },
                { value: 'SUSPENDED', label: 'Suspended' },
              ],
              onChange: setStatusFilter,
            },
          ]}
          enableBulkSelect={canManageStaff}
          selectedIds={selectedStaffIds}
          onSelectionChange={setSelectedStaffIds}
          bulkActions={[
            {
              label: 'Remove Selected Staff',
              icon: <IconTrash className="w-3.5 h-3.5" />,
              variant: 'destructive',
              onClick: async (ids: string[]) => {
                if (onBulkDeleteStaff) {
                  await onBulkDeleteStaff(ids);
                  setSelectedStaffIds(new Set());
                }
              },
            },
          ]}
        />
      ) : (
        <DataTable<StaffInvite>
          data={invitesList}
          keyExtractor={(item) => item.id}
          columns={inviteColumns}
          loading={false}
          emptyMessage="No pending invitations."
          menuDropdowns={[
            {
              id: 'section',
              label: 'View',
              value: activeSection,
              options: [
                { value: 'members', label: 'Active Members', count: staffList.length },
                { value: 'invites', label: 'Pending Invitations', count: invitesList.length },
              ],
              onChange: (val) => {
                setActiveSection(val as 'members' | 'invites');
                setSelectedStaffIds(new Set());
                setSelectedInviteIds(new Set());
              },
            },
          ]}
          enableBulkSelect={canManageStaff}
          selectedIds={selectedInviteIds}
          onSelectionChange={setSelectedInviteIds}
          bulkActions={[
            {
              label: 'Revoke Selected Invites',
              icon: <IconTrash className="w-3.5 h-3.5" />,
              variant: 'destructive',
              onClick: async (ids: string[]) => {
                if (onBulkDeleteInvites) {
                  await onBulkDeleteInvites(ids);
                  setSelectedInviteIds(new Set());
                }
              },
            },
          ]}
        />
      )}

      {/* Role Capabilities Reference Card */}
      <Card className="p-5">
        <CardTitle className="text-sm font-bold mb-1">Role Permissions Matrix</CardTitle>
        <CardDescription className="mb-4">
          Permissions assigned across standard organizational roles
        </CardDescription>
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3 text-xs">
          <div className="p-3.5 rounded-xl bg-muted/40 border border-border">
            <div className="font-bold text-amber-500 dark:text-amber-400 flex items-center gap-1.5">
              <IconShield className="w-3.5 h-3.5" /> OWNER
            </div>
            <div className="text-muted-foreground mt-1.5">
              Full control, manage settlements, audit logs, and assign permissions.
            </div>
          </div>
          <div className="p-3.5 rounded-xl bg-muted/40 border border-border">
            <div className="font-bold text-indigo-500 dark:text-indigo-400 flex items-center gap-1.5">
              <IconUsers className="w-3.5 h-3.5" /> MANAGER
            </div>
            <div className="text-muted-foreground mt-1.5">
              Staff management, UPI accounts, initiate refunds, view reports.
            </div>
          </div>
          <div className="p-3.5 rounded-xl bg-muted/40 border border-border">
            <div className="font-bold text-emerald-500 dark:text-emerald-400 flex items-center gap-1.5">
              <IconCreditCard className="w-3.5 h-3.5" /> CASHIER
            </div>
            <div className="text-muted-foreground mt-1.5">
              Generate dynamic QR codes, record customer payments, verify receipts.
            </div>
          </div>
          <div className="p-3.5 rounded-xl bg-muted/40 border border-border">
            <div className="font-bold text-cyan-500 dark:text-cyan-400 flex items-center gap-1.5">
              <IconActivity className="w-3.5 h-3.5" /> ACCOUNTANT
            </div>
            <div className="text-muted-foreground mt-1.5">
              Read-only transaction ledger access, export statements to CSV.
            </div>
          </div>
        </div>
      </Card>
    </div>
  );
}

export default StaffPage;
