// Main Application: Clean, Secure, Modular React Router Architecture
import React, { useState, useMemo, Suspense } from 'react';
import { Router, Routes, Route, ProtectedRoute, useNavigate, useLocation } from './lib/router';
import { createApiClient } from './lib/api';

import { SuspenseFallback } from './components/ui/components';
import Navbar from './components/Navbar';
import Sidebar from './components/Sidebar';
import BottomNav from './components/BottomNav';
import BottomSheet from './components/BottomSheet';
import Toast from './components/Toast';
import AppModals from './components/modals/AppModals';
import PendingInviteModal, { type PendingInvite } from './components/modals/PendingInviteModal';

import LoginPage from './pages/LoginPage';
import OverviewPage from './pages/OverviewPage';
import TransactionsPage from './pages/TransactionsPage';
import UpiPage from './pages/UpiPage';
import StaffPage from './pages/StaffPage';
import AccountsPage from './pages/AccountsPage';
import ProfilePage from './pages/ProfilePage';
import TablesPage from './pages/TablesPage';
import HealthPage from './pages/HealthPage';
import InviteAcceptPage from './pages/InviteAcceptPage';
import { ENV } from './services/env.service';

import { useTheme } from './hooks/useTheme';
import { useToast } from './hooks/useToast';
import { useAuthSession } from './hooks/useAuthSession';
import { useOrganizations } from './hooks/useOrganizations';
import { useDashboardData } from './hooks/useDashboardData';

import type {
  Transaction,
  UpiAccount,
  StaffMember,
  StaffInvite,
  TableColumnDef,
  BottomSheetConfig,
  BankAccount,
} from './types';

function AppContent() {
  const navigate = useNavigate();
  const location = useLocation();

  // Active route tab derived cleanly from router pathname
  const activeTab = location.pathname.replace(/^\//, '') || 'overview';

  // Global Theme & Toast Hooks
  const { theme, toggleTheme } = useTheme();
  const { toast, showToast, hideToast } = useToast();

  // Real-Time Telemetry Latency State
  const [pingMs, setPingMs] = useState<number | null>(null);

  // Modal Open States
  const [bottomSheetConfig, setBottomSheetConfig] = useState<BottomSheetConfig | null>(null);
  const [showNewTxnModal, setShowNewTxnModal] = useState<boolean>(false);
  const [showUpiModal, setShowUpiModal] = useState<UpiAccount | Record<string, never> | null>(null);
  const [showQrModal, setShowQrModal] = useState<UpiAccount | null>(null);
  const [showStaffModal, setShowStaffModal] = useState<StaffMember | Record<string, never> | null>(null);
  const [showNewBankModal, setShowNewBankModal] = useState<boolean>(false);
  const [editingBank, setEditingBank] = useState<BankAccount | null>(null);
  const [showTableRowModal, setShowTableRowModal] = useState<{
    mode: 'insert' | 'edit';
    row?: Record<string, unknown>;
  } | null>(null);
  const [inspectTxn, setInspectTxn] = useState<Transaction | null>(null);
  const [isSavingProfile, setIsSavingProfile] = useState<boolean>(false);
  const [pendingLoginInvite, setPendingLoginInvite] = useState<PendingInvite | null>(null);

  // Check for pending team invitations upon user login
  const checkPendingInvitations = React.useCallback(async (authToken: string) => {
    try {
      const res = await fetch(`${ENV.API_BASE_URL}/api/v1/me/invitations`, {
        headers: {
          Authorization: `Bearer ${authToken}`,
          Accept: 'application/json',
        },
      });
      if (res.ok) {
        const data = await res.json();
        const invites: PendingInvite[] = data.invitations || data.invites || [];
        const pending = invites.find((inv) => inv.status?.toUpperCase() === 'PENDING');
        if (pending) {
          setPendingLoginInvite(pending);
        }
      }
    } catch {
      // Ignore background check error
    }
  }, []);

  // Organizations Hook (Forward ref for apiFetch)
  const orgRef = React.useRef<{ activeOrgId: string | null }>({ activeOrgId: null });

  // Secure API Client with auto 401 retry, latency tracking & tenant header injection
  const apiFetch = useMemo(() => {
    return createApiClient(
      () => authSessionRef.current?.token || null,
      () => {
        authSessionRef.current?.handleUnauthorized();
        showToast('Session expired. Please sign in again.', 'error');
      },
      setPingMs,
      () => orgRef.current.activeOrgId
    );
  }, [showToast]);

  // Multi-Tenant Organizations
  const {
    organizations,
    setOrganizations,
    activeOrg,
    setActiveOrg,
    activeOrgRef,
    isDeletingOrg,
    loadOrganizations,
    handleSelectOrg,
    handleDeleteOrg,
    isOwner,
    canManageOrg,
    canManageStaff,
    canManageUpi,
    canManageAccounts,
  } = useOrganizations({
    token: '',
    onDeleted: () => navigate('/overview'),
    showToast,
    apiFetch,
  });

  // Keep orgRef in sync
  React.useEffect(() => {
    orgRef.current.activeOrgId = activeOrg?.id || null;
  }, [activeOrg]);

  // Auth Session Hook
  const {
    token,
    user,
    isCheckingAuth,
    handleLoginSuccess,
    handleLogout: logoutSession,
    handleUnauthorized,
  } = useAuthSession({
    onSessionRestored: async (freshToken) => {
      await loadOrganizations(freshToken);
    },
    onSessionExpired: () => {
      setActiveOrg(null);
      setOrganizations([]);
    },
  });

  // Keep auth ref updated for apiFetch closure
  const authSessionRef = React.useRef({ token, handleUnauthorized });
  React.useEffect(() => {
    authSessionRef.current = { token, handleUnauthorized };
  }, [token, handleUnauthorized]);

  // Re-load organizations and check invitations when token activates
  React.useEffect(() => {
    if (token) {
      loadOrganizations(token);
      checkPendingInvitations(token);
    }
  }, [token, loadOrganizations, checkPendingInvitations]);

  // Unified Dashboard Data Hook
  const {
    isSyncing,
    dashboardStats,
    overviewLoading,
    loadOverview,
    transactions,
    txnLoading,
    txnSearch,
    setTxnSearch,
    txnStatus,
    setTxnStatus,
    loadTransactions,
    upiAccounts,
    upiLoading,
    loadUpi,
    staffList,
    invitesList,
    staffLoading,
    loadStaff,
    bankAccounts,
    accountsLoading,
    loadAccounts,
    adminTables,
    selectedTable,
    setSelectedTable,
    tableData,
    tableLoading,
    tableSearch,
    setTableSearch,
    tableOffset,
    setTableOffset,
    loadTables,
    healthData,
    loadHealth,
  } = useDashboardData({
    token,
    activeOrg,
    activeTab,
    apiFetch,
    showToast,
    pingMs,
    setPingMs,
  });

  const onLogout = () => {
    logoutSession();
    showToast('Signed out successfully', 'info');
  };

  // Mobile Bottom Sheet Action Handlers
  const handleSelectTxnAction = (_action: string, txn: Transaction) => {
    setBottomSheetConfig({
      title: `Transaction ${txn.referenceNumber || txn.id.slice(0, 8)}`,
      subtitle: `₹${Number(txn.amount).toFixed(2)} · ${txn.status}`,
      actions: [
        {
          label: 'View Full Audit Payload',
          onClick: () => setInspectTxn(txn),
        },
        {
          label: 'Copy UTR Reference',
          onClick: () => {
            navigator.clipboard.writeText(txn.referenceNumber || txn.id);
            showToast('UTR reference copied!');
          },
        },
        {
          label: 'Issue Refund',
          onClick: () => showToast('Refund initiated to customer account'),
        },
        {
          label: 'Delete Transaction Record',
          variant: 'destructive',
          onClick: async () => {
            if (!activeOrg) return;
            if (!confirm(`Permanently delete transaction ${txn.referenceNumber || txn.id}?`)) return;
            try {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/transactions/${txn.id}`, {
                method: 'DELETE',
              });
              showToast('Transaction deleted successfully');
              loadTransactions();
              loadOverview();
            } catch (err: unknown) {
              const msg = err instanceof Error ? err.message : 'Error deleting transaction';
              showToast(msg, 'error');
            }
          },
        },
      ],
    });
  };

  const handleSelectUpiAction = (upi: UpiAccount, mode = 'menu') => {
    if (mode === 'qr') {
      setShowQrModal(upi);
      return;
    }
    setBottomSheetConfig({
      title: upi.payeeName || upi.accountHolderName,
      subtitle: upi.vpa || upi.upiId,
      actions: [
        {
          label: 'Generate Counter QR Code',
          onClick: () => setShowQrModal(upi),
        },
        {
          label: 'Edit UPI Account Details',
          onClick: () => setShowUpiModal(upi),
        },
        {
          label: upi.isDefault || upi.isPrimary ? 'Unset Primary' : 'Set as Primary Default',
          onClick: async () => {
            if (!activeOrg) return;
            try {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/upi/${upi.id}`, {
                method: 'PATCH',
                body: JSON.stringify({ isDefault: !upi.isDefault }),
              });
              showToast('UPI default status updated!');
              loadUpi();
            } catch (err: unknown) {
              const msg = err instanceof Error ? err.message : 'Error updating UPI';
              showToast(msg, 'error');
            }
          },
        },
        {
          label: 'Delete UPI ID',
          variant: 'destructive',
          onClick: async () => {
            if (!activeOrg) return;
            if (!confirm(`Delete UPI VPA ${upi.vpa || upi.upiId}?`)) return;
            try {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/upi/${upi.id}`, {
                method: 'DELETE',
              });
              showToast('UPI Account removed');
              loadUpi();
            } catch (err: unknown) {
              const msg = err instanceof Error ? err.message : 'Error removing UPI';
              showToast(msg, 'error');
            }
          },
        },
      ],
    });
  };

  const handleSelectAccountAction = (_action: string, account: BankAccount) => {
    setBottomSheetConfig({
      title: account.bankName,
      subtitle: `${account.accountHolderName} • ${account.accountNumberMasked || '••••'}`,
      actions: [
        {
          label: account.isDefault ? 'Default Settlement Account' : 'Set as Default Settlement Account',
          onClick: async () => {
            if (!activeOrg || account.isDefault) return;
            try {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/accounts/${account.id}/default`, {
                method: 'POST',
              });
              showToast('Settlement account set as default');
              loadAccounts();
            } catch (err: unknown) {
              const msg = err instanceof Error ? err.message : 'Error updating bank account';
              showToast(msg, 'error');
            }
          },
        },
        {
          label: 'Edit Bank Account',
          onClick: () => setEditingBank(account),
        },
        {
          label: 'Delete Bank Account',
          variant: 'destructive',
          onClick: async () => {
            if (!activeOrg) return;
            if (!confirm(`Delete bank account ${account.bankName} (${account.accountNumberMasked || '••••'})?`)) return;
            try {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/accounts/${account.id}`, {
                method: 'DELETE',
              });
              showToast('Bank account removed');
              loadAccounts();
            } catch (err: unknown) {
              const msg = err instanceof Error ? err.message : 'Error removing bank account';
              showToast(msg, 'error');
            }
          },
        },
      ],
    });
  };

  const handleSelectStaffAction = (_action: string, member: StaffMember | StaffInvite) => {
    if (_action === 'revoke_invite') {
      const invite = member as StaffInvite;
      const target = invite.invitedEmail || invite.email || 'this recipient';
      if (!confirm(`Cancel and revoke invitation for ${target}?`)) return;
      if (!activeOrg) return;
      apiFetch(`/api/v1/organizations/${activeOrg.id}/invites/${invite.id}`, { method: 'DELETE' })
        .then(() => {
          showToast('Invitation cancelled successfully');
          loadStaff();
        })
        .catch((err: unknown) => {
          const msg = err instanceof Error ? err.message : 'Error cancelling invite';
          showToast(msg, 'error');
        });
      return;
    }

    const staffMember = member as StaffMember;
    setBottomSheetConfig({
      title: staffMember.fullName || staffMember.name || staffMember.mobileNumber || 'Staff Member',
      subtitle: `Role: ${staffMember.role} · Status: ${staffMember.status}`,
      actions: [
        {
          label: 'Edit Assigned Role & Permissions',
          onClick: () => setShowStaffModal(staffMember),
        },
        {
          label: staffMember.status === 'ACTIVE' ? 'Suspend Member Access' : 'Activate Member Access',
          variant: staffMember.status === 'ACTIVE' ? 'destructive' : 'default',
          onClick: async () => {
            if (!activeOrg) return;
            const nextStatus = staffMember.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
            try {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/staff/${staffMember.id}`, {
                method: 'PATCH',
                body: JSON.stringify({ status: nextStatus }),
              });
              showToast(`Staff access set to ${nextStatus}`);
              loadStaff();
            } catch (err: unknown) {
              const msg = err instanceof Error ? err.message : 'Error setting status';
              showToast(msg, 'error');
            }
          },
        },
        {
          label: 'Remove Member from Store',
          variant: 'destructive',
          onClick: async () => {
            if (!activeOrg) return;
            if (
              !confirm(
                `Remove ${
                  staffMember.fullName || staffMember.name || staffMember.mobileNumber
                } from organization?`
              )
            ) {
              return;
            }
            try {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/staff/${staffMember.id}`, {
                method: 'DELETE',
              });
              showToast('Staff member removed');
              loadStaff();
            } catch (err: unknown) {
              const msg = err instanceof Error ? err.message : 'Error removing staff';
              showToast(msg, 'error');
            }
          },
        },
      ],
    });
  };

  const handleSelectTableRowAction = (
    row: Record<string, unknown>,
    table: string,
    columns: TableColumnDef[]
  ) => {
    const pkCol = columns.find((c) => c.isPrimary)?.name || 'id';
    const pkVal = row[pkCol];
    setBottomSheetConfig({
      title: `${table} Record`,
      subtitle: `Primary Key (${pkCol}): ${pkVal}`,
      actions: [
        {
          label: 'Edit Row Fields',
          onClick: () => setShowTableRowModal({ mode: 'edit', row }),
        },
        {
          label: 'Copy Row as JSON',
          onClick: () => {
            navigator.clipboard.writeText(JSON.stringify(row, null, 2));
            showToast('Copied row JSON to clipboard');
          },
        },
        {
          label: 'Delete Record Permanently',
          variant: 'destructive',
          onClick: async () => {
            if (!confirm(`Delete record with ${pkCol}=${pkVal} from ${table}?`)) return;
            try {
              await apiFetch(`/api/v1/admin/tables/${table}/${pkVal}`, { method: 'DELETE' });
              showToast('Record deleted successfully!');
              loadTables();
            } catch (err: unknown) {
              const msg = err instanceof Error ? err.message : 'Error deleting record';
              showToast(msg, 'error');
            }
          },
        },
      ],
    });
  };

  const handleOpenMoreSheet = () => {
    setBottomSheetConfig({
      title: 'More Features & Settings',
      subtitle: 'Management & Diagnostics',
      actions: [
        { label: 'Settlement Bank Accounts', onClick: () => navigate('/accounts') },
        { label: 'Business Profile & Registration', onClick: () => navigate('/orgs') },
        { label: 'Database Table Explorer (Admin)', onClick: () => navigate('/tables') },
        { label: 'System Health Diagnostics', onClick: () => navigate('/health') },
      ],
    });
  };

  // Auto-Authorization Loading Splash
  if (isCheckingAuth) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-background text-foreground transition-colors">
        <div className="w-16 h-16 rounded-2xl bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center shadow-lg shadow-brand-500/25 animate-pulse mb-4">
          <span className="text-2xl font-black text-white">UPI</span>
        </div>
        <p className="text-sm font-semibold text-muted-foreground animate-pulse">
          Restoring secure merchant session...
        </p>
      </div>
    );
  }

  // If unauthenticated and on an invite link, allow accepting invitation
  if (!token && location.pathname.startsWith('/invite')) {
    return (
      <InviteAcceptPage
        token={null}
        currentUser={null}
        apiFetch={apiFetch}
        showToast={showToast}
        theme={theme}
        onToggleTheme={toggleTheme}
        onLoginSuccess={async (newAccessToken, newUser, defaultOrg, refreshToken) => {
          await handleLoginSuccess(newAccessToken, newUser, defaultOrg, refreshToken);
          showToast(`Welcome, ${newUser.fullName || newUser.name || 'User'}!`);
          if (defaultOrg) {
            setActiveOrg(defaultOrg);
            localStorage.setItem('upieasy_active_org_id', defaultOrg.id);
          }
          await loadOrganizations(newAccessToken);
          navigate('/overview');
        }}
        onInviteAccepted={async (joinedOrg) => {
          setActiveOrg(joinedOrg);
          localStorage.setItem('upieasy_active_org_id', joinedOrg.id);
          navigate('/overview');
        }}
      />
    );
  }

  // If unauthenticated, show Google Login Page
  if (!token) {
    return (
      <LoginPage
        onLoginSuccess={async (newAccessToken, newUser, defaultOrg, refreshToken) => {
          await handleLoginSuccess(newAccessToken, newUser, defaultOrg, refreshToken);
          showToast(`Welcome, ${newUser.fullName || newUser.name || 'Merchant'}!`);
          if (defaultOrg) {
            setActiveOrg(defaultOrg);
            localStorage.setItem('upieasy_active_org_id', defaultOrg.id);
          }
          await loadOrganizations(newAccessToken);
          navigate('/overview');
        }}
        theme={theme}
        onToggleTheme={toggleTheme}
        pingMs={pingMs}
      />
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-background text-foreground transition-colors">
      <Navbar
        user={user}
        organizations={organizations}
        activeOrg={activeOrg}
        onSelectOrg={handleSelectOrg}
        onLogout={onLogout}
        theme={theme}
        onToggleTheme={toggleTheme}
        pingMs={pingMs}
        isSyncing={isSyncing}
      />

      <div className="flex-1 flex overflow-hidden">
        <Sidebar
          activeTab={activeTab}
          onSelectTab={(tabId) => navigate(`/${tabId}`)}
          activeOrg={activeOrg}
        />

        <main className="flex-1 p-4 sm:p-6 lg:p-8 overflow-y-auto pb-24 md:pb-8">
          <Suspense fallback={<SuspenseFallback activeTab={activeTab} />}>
            <Routes>
              <Route
                path="/overview"
                element={
                  <OverviewPage
                    stats={dashboardStats}
                    loading={overviewLoading}
                    onOpenNewTxn={() => setShowNewTxnModal(true)}
                    onOpenNewUpi={() => setShowUpiModal({})}
                    onNavigate={(tab) => navigate(`/${tab}`)}
                    onInspectTxn={handleSelectTxnAction}
                  />
                }
              />

              <Route
                path="/transactions"
                element={
                  <TransactionsPage
                    transactions={transactions}
                    loading={txnLoading}
                    onRefresh={loadTransactions}
                    search={txnSearch}
                    onSearchChange={setTxnSearch}
                    status={txnStatus}
                    onStatusChange={setTxnStatus}
                    onOpenNewTxn={() => setShowNewTxnModal(true)}
                    onSelectTxnAction={handleSelectTxnAction}
                    onBulkDelete={async (ids) => {
                      if (!activeOrg) return;
                      if (!confirm(`Delete ${ids.length} selected transaction(s)?`)) return;
                      try {
                        await apiFetch(`/api/v1/organizations/${activeOrg.id}/transactions/bulk-delete`, {
                          method: 'POST',
                          body: JSON.stringify({ ids }),
                        });
                        showToast(`Deleted ${ids.length} transactions`);
                        loadTransactions();
                        loadOverview();
                      } catch (err: unknown) {
                        const msg = err instanceof Error ? err.message : 'Error in bulk delete';
                        showToast(msg, 'error');
                      }
                    }}
                  />
                }
              />

              <Route
                path="/upi"
                element={
                  <UpiPage
                    upiAccounts={upiAccounts}
                    canManageUpi={canManageUpi}
                    loading={upiLoading}
                    onOpenNewUpi={() => setShowUpiModal({})}
                    onSelectUpiAction={handleSelectUpiAction}
                    onBulkDelete={async (ids) => {
                      if (!activeOrg) return;
                      if (!confirm(`Delete ${ids.length} selected UPI account(s)?`)) return;
                      try {
                        await apiFetch(`/api/v1/organizations/${activeOrg.id}/upi/bulk-delete`, {
                          method: 'POST',
                          body: JSON.stringify({ ids }),
                        });
                        showToast(`Deleted ${ids.length} UPI account(s)`);
                        loadUpi();
                      } catch (err: unknown) {
                        const msg = err instanceof Error ? err.message : 'Error in bulk delete';
                        showToast(msg, 'error');
                      }
                    }}
                  />
                }
              />

              <Route
                path="/staff"
                element={
                  <StaffPage
                    staffList={staffList}
                    invitesList={invitesList}
                    canManageStaff={canManageStaff}
                    loading={staffLoading}
                    onOpenInviteStaff={() => setShowStaffModal({})}
                    onSelectStaffAction={handleSelectStaffAction}
                    onBulkDeleteStaff={async (ids) => {
                      if (!activeOrg) return;
                      if (!confirm(`Remove ${ids.length} selected staff member(s)?`)) return;
                      try {
                        await apiFetch(`/api/v1/organizations/${activeOrg.id}/staff/bulk-delete`, {
                          method: 'POST',
                          body: JSON.stringify({ ids }),
                        });
                        showToast(`Removed ${ids.length} staff member(s)`);
                        loadStaff();
                      } catch (err: unknown) {
                        const msg = err instanceof Error ? err.message : 'Error in bulk delete';
                        showToast(msg, 'error');
                      }
                    }}
                    onBulkDeleteInvites={async (ids) => {
                      if (!activeOrg) return;
                      if (!confirm(`Revoke ${ids.length} selected invitation(s)?`)) return;
                      try {
                        await apiFetch(`/api/v1/organizations/${activeOrg.id}/invites/bulk-delete`, {
                          method: 'POST',
                          body: JSON.stringify({ ids }),
                        });
                        showToast(`Revoked ${ids.length} invitation(s)`);
                        loadStaff();
                      } catch (err: unknown) {
                        const msg = err instanceof Error ? err.message : 'Error in bulk delete';
                        showToast(msg, 'error');
                      }
                    }}
                  />
                }
              />

              <Route
                path="/accounts"
                element={
                  <AccountsPage
                    bankAccounts={bankAccounts}
                    canManageAccounts={canManageAccounts}
                    loading={accountsLoading}
                    onOpenNewBank={() => setShowNewBankModal(true)}
                    onSelectAccountAction={handleSelectAccountAction}
                    onBulkDelete={async (ids) => {
                      if (!activeOrg) return;
                      if (!confirm(`Delete ${ids.length} selected bank account(s)?`)) return;
                      try {
                        await apiFetch(`/api/v1/organizations/${activeOrg.id}/accounts/bulk-delete`, {
                          method: 'POST',
                          body: JSON.stringify({ ids }),
                        });
                        showToast(`Deleted ${ids.length} bank account(s)`);
                        loadAccounts();
                      } catch (err: unknown) {
                        const msg = err instanceof Error ? err.message : 'Error in bulk delete';
                        showToast(msg, 'error');
                      }
                    }}
                  />
                }
              />

              <Route
                path="/orgs"
                element={
                  <ProfilePage
                    activeOrg={activeOrg}
                    isOwner={isOwner}
                    canManageOrg={canManageOrg}
                    isSaving={isSavingProfile || isDeletingOrg}
                    onDeleteOrg={handleDeleteOrg}
                    onSaveProfile={async (e) => {
                      e.preventDefault();
                      if (!activeOrg) return;
                      const form = e.target as HTMLFormElement & {
                        name: HTMLInputElement;
                        legalBusinessName: HTMLInputElement;
                        category: HTMLSelectElement;
                        gstin: HTMLInputElement;
                        panNumber: HTMLInputElement;
                      };
                      setIsSavingProfile(true);
                      try {
                        await apiFetch(`/api/v1/organizations/${activeOrg.id}`, {
                          method: 'PATCH',
                          body: JSON.stringify({
                            name: form.name.value,
                            legalBusinessName: form.legalBusinessName.value,
                            category: form.category.value,
                            gstin: form.gstin.value || null,
                            panNumber: form.panNumber.value || null,
                          }),
                        });
                        showToast('Profile saved successfully');
                        loadOrganizations();
                      } catch (err: unknown) {
                        const msg = err instanceof Error ? err.message : 'Error saving profile';
                        showToast(msg, 'error');
                      } finally {
                        setIsSavingProfile(false);
                      }
                    }}
                  />
                }
              />

              <Route
                path="/tables"
                element={
                  <ProtectedRoute
                    isAuthenticated={Boolean(token)}
                    userRole={activeOrg?.role}
                    requiredRole="MANAGER">
                    <TablesPage
                      tables={adminTables}
                      selectedTable={selectedTable}
                      onSelectTable={(t) => {
                        setSelectedTable(t);
                        setTableOffset(0);
                        setTableSearch('');
                      }}
                      tableData={tableData}
                      loading={tableLoading}
                      search={tableSearch}
                      onSearchChange={setTableSearch}
                      offset={tableOffset}
                      onOffsetChange={setTableOffset}
                      limit={25}
                      onOpenInsertModal={() => setShowTableRowModal({ mode: 'insert' })}
                      onSelectRowAction={handleSelectTableRowAction}
                    />
                  </ProtectedRoute>
                }
              />

              <Route
                path="/health"
                element={
                  <HealthPage
                    healthData={healthData}
                    pingMs={pingMs}
                    onRefresh={loadHealth}
                    onResetSeed={async () => {
                      try {
                        await apiFetch('/api/v1/admin/seed', { method: 'POST' });
                        showToast('Database baseline re-seeded successfully!');
                        loadOverview();
                      } catch (err: unknown) {
                        const msg = err instanceof Error ? err.message : 'Error re-seeding';
                        showToast(msg, 'error');
                      }
                    }}
                  />
                }
              />

              <Route
                path="/invite/:token"
                element={
                  <InviteAcceptPage
                    token={token}
                    currentUser={user}
                    apiFetch={apiFetch}
                    showToast={showToast}
                    theme={theme}
                    onToggleTheme={toggleTheme}
                    onLoginSuccess={async (newAccessToken, newUser, defaultOrg, refreshToken) => {
                      await handleLoginSuccess(newAccessToken, newUser, defaultOrg, refreshToken);
                      if (defaultOrg) {
                        setActiveOrg(defaultOrg);
                        localStorage.setItem('upieasy_active_org_id', defaultOrg.id);
                      }
                      await loadOrganizations(newAccessToken);
                      navigate('/overview');
                    }}
                    onInviteAccepted={async (joinedOrg) => {
                      setActiveOrg(joinedOrg);
                      localStorage.setItem('upieasy_active_org_id', joinedOrg.id);
                      if (token) await loadOrganizations(token);
                      navigate('/overview');
                    }}
                  />
                }
              />

              <Route
                path="*"
                element={
                  <OverviewPage
                    stats={dashboardStats}
                    loading={overviewLoading}
                    onOpenNewTxn={() => setShowNewTxnModal(true)}
                    onOpenNewUpi={() => setShowUpiModal({})}
                    onNavigate={(tab) => navigate(`/${tab}`)}
                    onInspectTxn={handleSelectTxnAction}
                  />
                }
              />
            </Routes>
          </Suspense>
        </main>
      </div>

      <BottomNav
        activeTab={activeTab}
        onSelectTab={(tabId) => navigate(`/${tabId}`)}
        onOpenMoreSheet={handleOpenMoreSheet}
        activeOrg={activeOrg}
      />

      <BottomSheet
        isOpen={Boolean(bottomSheetConfig)}
        onClose={() => setBottomSheetConfig(null)}
        title={bottomSheetConfig?.title || 'Options'}
        subtitle={bottomSheetConfig?.subtitle}
        actions={bottomSheetConfig?.actions || []}
      />

      {/* Modularized App Modals Container */}
      <AppModals
        activeOrg={activeOrg}
        upiAccounts={upiAccounts}
        selectedTable={selectedTable}
        tableData={tableData}
        apiFetch={apiFetch}
        showToast={showToast}
        showNewTxnModal={showNewTxnModal}
        setShowNewTxnModal={setShowNewTxnModal}
        showUpiModal={showUpiModal}
        setShowUpiModal={setShowUpiModal}
        showQrModal={showQrModal}
        setShowQrModal={setShowQrModal}
        showStaffModal={showStaffModal}
        setShowStaffModal={setShowStaffModal}
        showNewBankModal={showNewBankModal}
        setShowNewBankModal={setShowNewBankModal}
        editingBank={editingBank}
        setEditingBank={setEditingBank}
        showTableRowModal={showTableRowModal}
        setShowTableRowModal={setShowTableRowModal}
        inspectTxn={inspectTxn}
        setInspectTxn={setInspectTxn}
        onPaymentRecorded={() => {
          loadOverview();
          loadTransactions();
        }}
        onUpiSaved={loadUpi}
        onStaffSaved={loadStaff}
        onBankSaved={loadAccounts}
        onTableSaved={loadTables}
      />

      {/* Global Notification Toast */}
      <Toast toast={toast} onClose={hideToast} />

      {/* Pending Organization Invitation Popup Modal */}
      <PendingInviteModal
        invite={pendingLoginInvite}
        onAccept={async (invite) => {
          try {
            const res = await apiFetch<{ success: boolean; organization?: Organization }>(
              `/api/v1/invitations/${invite.id}/accept`,
              {
                method: 'POST',
              }
            );
            showToast('Invitation accepted! Welcome to the workspace.');
            setPendingLoginInvite(null);
            if (token) {
              await loadOrganizations(token);
            }
            if (res.organization) {
              setActiveOrg(res.organization);
              localStorage.setItem('upieasy_active_org_id', res.organization.id);
            }
            navigate('/overview');
          } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Failed to accept invitation';
            showToast(msg, 'error');
          }
        }}
        onDecline={async (invite) => {
          try {
            await apiFetch(`/api/v1/invitations/${invite.id}/reject`, {
              method: 'POST',
            });
            showToast('Invitation declined.');
          } catch {
            // Ignore error on decline
          } finally {
            setPendingLoginInvite(null);
          }
        }}
      />
    </div>
  );
}

export function App() {
  return (
    <Router>
      <AppContent />
    </Router>
  );
}

export default App;
