import React, { useState, useEffect, useCallback } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback } from '@/components/ui/components';
import { DataTable } from '@/components/ui/DataTable';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Switch } from '@/components/ui/switch';
import {
  IconPlus,
  IconMoreVertical,
  IconShield,
  IconUsers,
  IconCreditCard,
  IconActivity,
  IconTrash,
  IconSliders,
  IconRefreshCw,
} from '@/components/ui/icons';
import { StaffPageSkeleton } from '@/components/ui/Skeleton';
import type { StaffMember, StaffInvite, StaffPageProps, RoleDefinition, PermissionDefinition } from '@/types';

const defaultRoles: RoleDefinition[] = [
  { id: 'role_owner', name: 'OWNER', description: 'Full business control, wildcard * permissions', permissions: [] },
  { id: 'role_manager', name: 'MANAGER', description: 'Staff, UPI accounts, transactions, refunds & reports', permissions: ['perm_tx_read', 'perm_tx_export', 'perm_tx_create', 'perm_tx_refund', 'perm_tx_delete', 'perm_acc_read', 'perm_upi_read', 'perm_upi_manage', 'perm_qr_create', 'perm_staff_read', 'perm_staff_manage', 'perm_rep_read'] },
  { id: 'role_cashier', name: 'CASHIER', description: 'Payment initiation, dynamic QR counter & ledger view', permissions: ['perm_tx_read', 'perm_tx_create', 'perm_qr_create', 'perm_upi_read'] },
  { id: 'role_accountant', name: 'ACCOUNTANT', description: 'Ledger read-only, reports & CSV exports', permissions: ['perm_tx_read', 'perm_tx_export', 'perm_rep_read', 'perm_acc_read', 'perm_upi_read'] },
];

const defaultPermissions: PermissionDefinition[] = [
  { id: 'perm_tx_read', name: 'transactions.read', description: 'View payment transactions & settlement history', category: 'transactions' },
  { id: 'perm_tx_export', name: 'transactions.export', description: 'Export reports to Excel & CSV spreadsheets', category: 'transactions' },
  { id: 'perm_tx_create', name: 'transactions.create', description: 'Record manual payments & initiate transactions', category: 'transactions' },
  { id: 'perm_tx_refund', name: 'transactions.refund', description: 'Process payment refunds back to customers', category: 'transactions' },
  { id: 'perm_tx_delete', name: 'transactions.delete', description: 'Delete transactions from ledger history', category: 'transactions' },
  { id: 'perm_evt_ingest', name: 'payment_events.ingest', description: 'Auto-detect and capture payment notifications & SMS', category: 'transactions' },
  { id: 'perm_acc_read', name: 'accounts.read', description: 'View settlement bank accounts', category: 'accounts' },
  { id: 'perm_acc_manage', name: 'accounts.manage', description: 'Add, update, or remove linked bank accounts', category: 'accounts' },
  { id: 'perm_upi_read', name: 'upi.read', description: 'View active UPI IDs and VPAs', category: 'upi' },
  { id: 'perm_upi_manage', name: 'upi.manage', description: 'Configure and manage business UPI handles', category: 'upi' },
  { id: 'perm_qr_create', name: 'qr.create', description: 'Generate custom counter and customer QR codes', category: 'qr' },
  { id: 'perm_staff_read', name: 'staff.read', description: 'View team members and staff list', category: 'staff' },
  { id: 'perm_staff_manage', name: 'staff.manage', description: 'Invite staff, assign roles, or remove members', category: 'staff' },
  { id: 'perm_rep_read', name: 'reports.read', description: 'Access sales reports and business analytics', category: 'reports' },
  { id: 'perm_org_manage', name: 'organization.manage', description: 'Manage organization settings and business profile', category: 'organization' },
];

const categoryLabels: Record<string, string> = {
  transactions: 'Transactions & Ledger',
  accounts: 'Bank Accounts',
  upi: 'UPI Handles & VPAs',
  qr: 'QR Codes & Counter Pay',
  staff: 'Staff & Team Management',
  reports: 'Reports & Analytics',
  organization: 'Business Settings & Profile',
};

export function StaffPage({
  staffList,
  invitesList,
  canManageStaff = true,
  loading = false,
  onOpenInviteStaff,
  onSelectStaffAction,
  onBulkDeleteStaff,
  onBulkDeleteInvites,
  activeOrg,
  isOwner = false,
  apiFetch,
  showToast,
}: StaffPageProps) {
  const [selectedStaffIds, setSelectedStaffIds] = useState<Set<string>>(new Set());
  const [selectedInviteIds, setSelectedInviteIds] = useState<Set<string>>(new Set());
  const [staffSearch, setStaffSearch] = useState<string>('');
  const [roleFilter, setRoleFilter] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [activeSection, setActiveSection] = useState<'members' | 'invites'>('members');

  // Role permissions interactive state
  const [roles, setRoles] = useState<RoleDefinition[]>([]);
  const [allPermissions, setAllPermissions] = useState<PermissionDefinition[]>([]);
  const [loadingRoles, setLoadingRoles] = useState<boolean>(false);
  const [editingRole, setEditingRole] = useState<RoleDefinition | null>(null);
  const [selectedPermIds, setSelectedPermIds] = useState<Set<string>>(new Set());
  const [isSavingPerms, setIsSavingPerms] = useState<boolean>(false);

  const loadRolesAndPermissions = useCallback(async () => {
    if (!activeOrg?.id || !apiFetch) return;
    setLoadingRoles(true);
    try {
      const res = await apiFetch<{
        success: boolean;
        roles: RoleDefinition[];
        permissions: PermissionDefinition[];
      }>(`/api/v1/organizations/${activeOrg.id}/roles`);
      if (res && res.success) {
        if (res.roles && res.roles.length > 0) {
          setRoles(res.roles);
        }
        if (res.permissions && res.permissions.length > 0) {
          setAllPermissions(res.permissions);
        }
      }
    } catch (err) {
      console.warn('Could not fetch remote roles, using defaults:', err);
    } finally {
      setLoadingRoles(false);
    }
  }, [activeOrg?.id, apiFetch]);

  useEffect(() => {
    loadRolesAndPermissions();
  }, [loadRolesAndPermissions]);

  const displayRoles = roles.length > 0 ? roles : defaultRoles;
  const displayPerms = allPermissions.length > 0 ? allPermissions : defaultPermissions;

  const handleOpenEditRole = (role: RoleDefinition) => {
    setEditingRole(role);
    setSelectedPermIds(new Set(role.permissions || []));
  };

  const handleTogglePermission = (permId: string) => {
    setSelectedPermIds((prev) => {
      const next = new Set(prev);
      if (next.has(permId)) {
        next.delete(permId);
      } else {
        next.add(permId);
      }
      return next;
    });
  };

  const handleSavePermissions = async () => {
    if (!editingRole || !activeOrg?.id || !apiFetch) return;
    setIsSavingPerms(true);
    try {
      await apiFetch(
        `/api/v1/organizations/${activeOrg.id}/roles/${editingRole.id}/permissions`,
        {
          method: 'PATCH',
          body: JSON.stringify({
            permissionIds: Array.from(selectedPermIds),
          }),
        }
      );
      showToast?.(`Permissions updated successfully for ${editingRole.name}`, 'success');
      setRoles((prev) =>
        prev.map((r) =>
          r.id === editingRole.id ? { ...r, permissions: Array.from(selectedPermIds) } : r
        )
      );
      setEditingRole(null);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update permissions';
      showToast?.(msg, 'error');
    } finally {
      setIsSavingPerms(false);
    }
  };

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
      header: 'Actions',
      headerClassName: 'text-right',
      cellClassName: 'text-right',
      cell: (member: StaffMember) => (
        <Button
          variant="outline"
          size="sm"
          onClick={() => onSelectStaffAction('options', member)}
          className="rounded-lg h-7 px-2 text-xs gap-1">
          <IconMoreVertical className="w-3 h-3" />
        </Button>
      ),
    },
  ];

  const inviteColumns = [
    {
      header: 'Recipient',
      cell: (invite: StaffInvite) => (
        <div>
          <div className="font-medium text-foreground text-xs">{invite.invitedName || 'Invited Staff'}</div>
          <div className="text-[10px] font-mono text-muted-foreground">{invite.email || invite.invitedEmail}</div>
        </div>
      ),
    },
    {
      header: 'Assigned Role',
      cell: (invite: StaffInvite) => (
        <Badge variant="outline" className="uppercase tracking-wider text-[10px]">
          {invite.role}
        </Badge>
      ),
    },
    {
      header: 'Expires',
      cell: (invite: StaffInvite) => (
        <span className="text-xs text-muted-foreground">
          {new Date(invite.expiresAt).toLocaleDateString()}
        </span>
      ),
    },
    {
      header: 'Actions',
      headerClassName: 'text-right',
      cellClassName: 'text-right',
      cell: (invite: StaffInvite) => (
        <Button
          variant="outline"
          size="sm"
          onClick={() => onSelectStaffAction('options', invite)}
          className="rounded-lg h-7 px-2 text-xs gap-1">
          <IconMoreVertical className="w-3 h-3" />
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
            Staff & Team Members
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Manage cashier logins, store managers, and RBAC module permission toggles
          </p>
        </div>
        <div className="flex items-center gap-2">
          {canManageStaff && (
            <Button
              variant="brand"
              size="sm"
              onClick={onOpenInviteStaff}
              className="rounded-xl gap-1.5 font-bold shadow-sm">
              <IconPlus className="w-3.5 h-3.5" />
              <span>Invite Member</span>
            </Button>
          )}
        </div>
      </div>

      {/* Tabs and DataTable */}
      {activeSection === 'members' ? (
        <DataTable<StaffMember>
          data={filteredStaff}
          keyExtractor={(item) => item.id}
          columns={staffColumns}
          loading={false}
          emptyMessage="No staff members found matching criteria."
          search={staffSearch}
          onSearchChange={setStaffSearch}
          searchPlaceholder="Search staff by name, email or phone..."
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
                { value: 'ACCOUNTANT', label: 'Accountant' },
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
                { value: 'INACTIVE', label: 'Inactive' },
              ],
              onChange: setStatusFilter,
            },
          ]}
          enableBulkSelect={canManageStaff}
          selectedIds={selectedStaffIds}
          onSelectionChange={setSelectedStaffIds}
          bulkActions={[
            {
              label: 'Remove Selected Members',
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

      {/* Interactive Role Permissions Matrix Card */}
      <Card className="p-6 rounded-2xl border border-border shadow-xs">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-4">
          <div>
            <CardTitle className="text-base font-bold flex items-center gap-2">
              <IconShield className="w-4 h-4 text-primary" />
              Role Permissions & Access Control Matrix
            </CardTitle>
            <CardDescription className="text-xs mt-0.5">
              Live module capability switches assigned across organizational roles
            </CardDescription>
          </div>
          {isOwner && (
            <Button
              variant="outline"
              size="sm"
              onClick={loadRolesAndPermissions}
              className="rounded-xl h-8 gap-1.5 text-xs self-start sm:self-auto">
              <IconRefreshCw className={`w-3 h-3 ${loadingRoles ? 'animate-spin' : ''}`} />
              <span>Refresh Roles</span>
            </Button>
          )}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {displayRoles.map((role) => {
            const isOwnerRole = role.name.toUpperCase() === 'OWNER';
            const roleColorClass =
              role.name === 'OWNER'
                ? 'text-amber-500 border-amber-500/20 bg-amber-500/5'
                : role.name === 'MANAGER'
                ? 'text-indigo-500 border-indigo-500/20 bg-indigo-500/5'
                : role.name === 'CASHIER'
                ? 'text-emerald-500 border-emerald-500/20 bg-emerald-500/5'
                : 'text-cyan-500 border-cyan-500/20 bg-cyan-500/5';

            const RoleIcon =
              role.name === 'OWNER'
                ? IconShield
                : role.name === 'MANAGER'
                ? IconUsers
                : role.name === 'CASHIER'
                ? IconCreditCard
                : IconActivity;

            const assignedCount = isOwnerRole
              ? displayPerms.length
              : (role.permissions || []).length;

            return (
              <div
                key={role.id || role.name}
                className="p-4 rounded-2xl bg-card border border-border hover:border-muted-foreground/30 transition-all flex flex-col justify-between space-y-4">
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <div className={`font-bold text-xs flex items-center gap-1.5 px-2.5 py-1 rounded-lg border ${roleColorClass}`}>
                      <RoleIcon className="w-3.5 h-3.5" />
                      <span>{role.name}</span>
                    </div>
                    {isOwnerRole ? (
                      <Badge variant="warning" className="text-[10px]">
                        Full Wildcard (*)
                      </Badge>
                    ) : (
                      <Badge variant="outline" className="text-[10px] font-mono">
                        {assignedCount} / {displayPerms.length}
                      </Badge>
                    )}
                  </div>

                  <p className="text-xs text-muted-foreground leading-relaxed pt-1">
                    {role.description ||
                      (isOwnerRole
                        ? 'Full business control, unmodifiable administrative wildcard access.'
                        : `Standard operational privileges assigned for ${role.name}.`)}
                  </p>
                </div>

                <div className="pt-2 border-t border-border/60">
                  {isOwnerRole ? (
                    <div className="text-[11px] text-muted-foreground flex items-center gap-1.5 font-medium">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                      <span>Always unrestricted</span>
                    </div>
                  ) : isOwner ? (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleOpenEditRole(role)}
                      className="w-full rounded-xl h-8 gap-1.5 text-xs font-semibold hover:border-primary hover:text-primary">
                      <IconSliders className="w-3.5 h-3.5" />
                      <span>Configure Switches</span>
                    </Button>
                  ) : (
                    <div className="text-[11px] text-muted-foreground">
                      {assignedCount} active permission modules
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </Card>

      {/* Interactive On/Off Permission Drawer / Dialog */}
      {editingRole && (
        <Dialog open={true} onOpenChange={() => setEditingRole(null)}>
          <DialogContent size="lg" className="max-h-[85vh] flex flex-col p-0">
            <DialogHeader className="p-6 pb-4 border-b border-border">
              <div>
                <DialogTitle className="text-lg font-bold flex items-center gap-2">
                  <IconSliders className="w-5 h-5 text-primary" />
                  Configure {editingRole.name} Permissions
                </DialogTitle>
                <DialogDescription className="text-xs text-muted-foreground mt-1">
                  Toggle on/off capability switches for staff assigned the {editingRole.name} role in{' '}
                  <span className="font-semibold text-foreground">{activeOrg?.name || 'this organization'}</span>.
                </DialogDescription>
              </div>
            </DialogHeader>

            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {Object.entries(
                displayPerms.reduce<Record<string, PermissionDefinition[]>>((acc, p) => {
                  const cat = p.category || 'other';
                  if (!acc[cat]) acc[cat] = [];
                  acc[cat].push(p);
                  return acc;
                }, {})
              ).map(([catKey, catPerms]) => (
                <div key={catKey} className="space-y-3">
                  <div className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-primary" />
                    {categoryLabels[catKey] || catKey}
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
                    {catPerms.map((perm) => {
                      const isChecked = selectedPermIds.has(perm.id);
                      return (
                        <div
                          key={perm.id}
                          onClick={() => handleTogglePermission(perm.id)}
                          className={`p-3 rounded-xl border transition-all cursor-pointer select-none flex items-start justify-between gap-3 ${
                            isChecked
                              ? 'bg-primary/5 border-primary/30 shadow-xs'
                              : 'bg-card border-border hover:border-muted-foreground/30'
                          }`}>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-1.5">
                              <span className="font-semibold text-xs text-foreground truncate">
                                {perm.name}
                              </span>
                            </div>
                            <p className="text-[11px] text-muted-foreground mt-0.5 line-clamp-2">
                              {perm.description}
                            </p>
                          </div>
                          <Switch
                            checked={isChecked}
                            onCheckedChange={() => handleTogglePermission(perm.id)}
                            className="mt-0.5"
                          />
                        </div>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>

            <DialogFooter className="p-4 border-t border-border flex justify-between items-center sm:justify-between bg-muted/20">
              <div className="text-xs text-muted-foreground">
                <span className="font-bold text-foreground">{selectedPermIds.size}</span> of {displayPerms.length} permissions enabled
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setEditingRole(null)}
                  disabled={isSavingPerms}
                  className="rounded-xl text-xs h-9">
                  Cancel
                </Button>
                <Button
                  variant="brand"
                  size="sm"
                  onClick={handleSavePermissions}
                  disabled={isSavingPerms}
                  className="rounded-xl text-xs h-9 gap-1.5 font-bold">
                  {isSavingPerms ? 'Saving...' : 'Save Permissions'}
                </Button>
              </div>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
}

export default StaffPage;
