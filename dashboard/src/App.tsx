// Main Application: Clean, Secure, Declarative React Router Architecture (Strictly Typed)
import React, { useState, useEffect, useMemo, Suspense } from 'react';
import { Router, Routes, Route, ProtectedRoute, useNavigate, useLocation } from './lib/router';
import { getStoredAuth, saveAuth, clearAuth } from './lib/auth';
import { createApiClient } from './lib/api';

import { SuspenseFallback } from './components/ui/components';
import Navbar from './components/Navbar';
import Sidebar from './components/Sidebar';
import BottomNav from './components/BottomNav';
import BottomSheet from './components/BottomSheet';

import LoginPage from './pages/LoginPage';
import OverviewPage from './pages/OverviewPage';
import TransactionsPage from './pages/TransactionsPage';
import UpiPage from './pages/UpiPage';
import StaffPage from './pages/StaffPage';
import AccountsPage from './pages/AccountsPage';
import ProfilePage from './pages/ProfilePage';
import TablesPage from './pages/TablesPage';
import HealthPage from './pages/HealthPage';

import RecordPaymentModal from './components/modals/RecordPaymentModal';
import UpiAccountModal from './components/modals/UpiAccountModal';
import QrCodeModal from './components/modals/QrCodeModal';
import StaffModal from './components/modals/StaffModal';
import BankAccountModal from './components/modals/BankAccountModal';
import TableRowModal from './components/modals/TableRowModal';
import TransactionDetailModal from './components/modals/TransactionDetailModal';

import {
  IconQrCode,
  IconEdit,
  IconStar,
  IconTrash2,
  IconLock,
  IconCopy,
  IconEye,
  IconArrowUpRight,
  IconLandmark,
  IconBuilding,
  IconDatabase,
  IconHeartPulse,
} from './components/ui/icons';

import type {
  User,
  Organization,
  Transaction,
  UpiAccount,
  StaffMember,
  StaffInvite,
  BankAccount,
  DashboardStats,
  TableColumnDef,
  TableSchemaItem,
  TableData,
  HealthData,
  Theme,
  BottomSheetConfig,
  AuthState,
} from './types';
import ENV from './services/env.service';

interface ToastState {
  message: string;
  type: 'success' | 'error' | 'info';
}

function AppContent() {
  const navigate = useNavigate();
  const location = useLocation();

  // Active tab derived cleanly from router pathname
  const activeTab = location.pathname.replace(/^\//, '') || 'overview';

  // Theme State
  const [theme, setTheme] = useState<Theme>(
    () => (localStorage.getItem('upieasy_theme') as Theme) || 'dark'
  );

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
      root.classList.remove('light');
    } else {
      root.classList.remove('dark');
      root.classList.add('light');
    }
    localStorage.setItem('upieasy_theme', theme);
  }, [theme]);

  // Auth State & Token Expiration Guard
  const [auth, setAuth] = useState<AuthState>(() => getStoredAuth());
  const token = auth.token;
  const user = auth.user;

  // Multi-Tenant Org State
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [activeOrg, setActiveOrg] = useState<Organization | null>(null);

  // Real-Time Telemetry State
  const [pingMs, setPingMs] = useState<number | null>(null);
  const [isSyncing, setIsSyncing] = useState<boolean>(false);

  // Entities
  const [dashboardStats, setDashboardStats] = useState<DashboardStats | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [txnLoading, setTxnLoading] = useState<boolean>(false);
  const [txnSearch, setTxnSearch] = useState<string>('');
  const [txnStatus, setTxnStatus] = useState<string>('');

  const [upiAccounts, setUpiAccounts] = useState<UpiAccount[]>([]);
  const [staffList, setStaffList] = useState<StaffMember[]>([]);
  const [invitesList, setInvitesList] = useState<StaffInvite[]>([]);
  const [bankAccounts, setBankAccounts] = useState<BankAccount[]>([]);

  // Database Tables State (Admin)
  const [adminTables, setAdminTables] = useState<TableSchemaItem[]>([]);
  const [selectedTable, setSelectedTable] = useState<string>('transactions');
  const [tableData, setTableData] = useState<TableData>({ rows: [], columns: [], total: 0 });
  const [tableLoading, setTableLoading] = useState<boolean>(false);
  const [tableSearch, setTableSearch] = useState<string>('');
  const [tableOffset, setTableOffset] = useState<number>(0);

  // Health
  const [healthData, setHealthData] = useState<HealthData | null>(null);

  // Modals & Sheets
  const [bottomSheetConfig, setBottomSheetConfig] = useState<BottomSheetConfig | null>(null);
  const [showNewTxnModal, setShowNewTxnModal] = useState<boolean>(false);
  const [showUpiModal, setShowUpiModal] = useState<UpiAccount | Record<string, never> | null>(null);
  const [showQrModal, setShowQrModal] = useState<UpiAccount | null>(null);
  const [showStaffModal, setShowStaffModal] = useState<StaffMember | Record<string, never> | null>(null);
  const [showNewBankModal, setShowNewBankModal] = useState<boolean>(false);
  const [showTableRowModal, setShowTableRowModal] = useState<{
    mode: 'insert' | 'edit';
    row?: Record<string, unknown>;
  } | null>(null);
  const [inspectTxn, setInspectTxn] = useState<Transaction | null>(null);

  // Toast
  const [toast, setToast] = useState<ToastState | null>(null);
  const showToast = (message: string, type: 'success' | 'error' | 'info' = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3500);
  };

  // Secure API Client with auto 401 logout & latency tracking
  const apiFetch = useMemo(() => {
    return createApiClient(
      () => token,
      () => {
        setAuth({ token: '', user: null });
        showToast('Session expired. Please sign in again.', 'error');
      },
      setPingMs
    );
  }, [token]);

  // Load Organizations
  const loadOrganizations = async (authToken = token) => {
    try {
      const res = await fetch(`${ENV.API_BASE_URL}/api/v1/organizations`, {
        headers: { 
          Authorization: `Bearer ${authToken}`,
          Accept: 'application/json',
        },
      });
      const contentType = res.headers.get('content-type') || '';
      if (!contentType.includes('application/json')) return;
      const data = await res.json();
      if (data.success && data.organizations?.length > 0) {
        setOrganizations(data.organizations);
        const savedOrgId = localStorage.getItem('upieasy_active_org_id');
        const def =
          (savedOrgId && data.organizations.find((o: Organization) => o.id === savedOrgId)) ||
          data.organizations[0];
        setActiveOrg(def);
      }
    } catch (err) {
      console.error(err);
    }
  };

  // Auth Actions
  const handleLoginSuccess = async (newAccessToken: string, newUser: User) => {
    saveAuth(newAccessToken, newUser);
    setAuth({ token: newAccessToken, user: newUser });
    showToast(`Welcome, ${newUser.fullName || newUser.name || 'Merchant'}!`);
    await loadOrganizations(newAccessToken);
    navigate('/overview');
  };

  const handleLogout = () => {
    clearAuth();
    setAuth({ token: '', user: null });
    setActiveOrg(null);
    setOrganizations([]);
    showToast('Signed out successfully', 'info');
  };

  const handleSelectOrg = (org: Organization) => {
    setActiveOrg(org);
    localStorage.setItem('upieasy_active_org_id', org.id);
  };

  useEffect(() => {
    if (token) loadOrganizations();
  }, [token]);

  // Tab Loaders
  const loadOverview = async () => {
    if (!activeOrg) return;
    try {
      const data = await apiFetch<any>(`/api/v1/organizations/${activeOrg.id}/dashboard`);
      if (data.success) setDashboardStats(data.dashboard);
    } catch {}
  };

  const loadTransactions = async (silent = false) => {
    if (!activeOrg) return;
    if (!silent) setTxnLoading(true);
    try {
      let url = `/api/v1/organizations/${activeOrg.id}/transactions?limit=50`;
      if (txnStatus) url += `&status=${txnStatus}`;
      if (txnSearch) url += `&search=${encodeURIComponent(txnSearch)}`;
      const data = await apiFetch<any>(url);
      if (data.success) setTransactions(data.transactions || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error loading ledger';
      if (!silent) showToast(msg, 'error');
    } finally {
      if (!silent) setTxnLoading(false);
    }
  };

  const loadUpi = async () => {
    if (!activeOrg) return;
    try {
      const data = await apiFetch<any>(`/api/v1/organizations/${activeOrg.id}/upi`);
      if (data.success) setUpiAccounts(data.upiAccounts || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error loading UPI';
      showToast(msg, 'error');
    }
  };

  const loadStaff = async () => {
    if (!activeOrg) return;
    try {
      const [staffRes, invitesRes] = await Promise.all([
        apiFetch<any>(`/api/v1/organizations/${activeOrg.id}/staff`),
        apiFetch<any>(`/api/v1/organizations/${activeOrg.id}/invites`),
      ]);
      if (staffRes.success) setStaffList(staffRes.staff || []);
      if (invitesRes.success) setInvitesList(invitesRes.invites || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error loading staff';
      showToast(msg, 'error');
    }
  };

  const loadAccounts = async () => {
    if (!activeOrg) return;
    try {
      const data = await apiFetch<any>(`/api/v1/organizations/${activeOrg.id}/accounts`);
      if (data.success) setBankAccounts(data.accounts || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error loading accounts';
      showToast(msg, 'error');
    }
  };

  const loadTables = async () => {
    try {
      const data = await apiFetch<any>('/api/v1/admin/tables');
      if (data.success) {
        setAdminTables(data.tables || []);
        if (data.tables?.length > 0 && !selectedTable) {
          setSelectedTable(data.tables[0].name);
        }
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error loading tables';
      showToast(msg, 'error');
    }
  };

  const loadTableData = async () => {
    setTableLoading(true);
    try {
      let url = `/api/v1/admin/tables/${selectedTable}?limit=25&offset=${tableOffset}`;
      if (tableSearch) url += `&search=${encodeURIComponent(tableSearch)}`;
      const data = await apiFetch<any>(url);
      if (data.success) setTableData(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error loading table data';
      showToast(msg, 'error');
    } finally {
      setTableLoading(false);
    }
  };

  const loadHealth = async () => {
    try {
      const data = await apiFetch<any>('/api/v1/admin/health');
      if (data.success) setHealthData(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error loading health';
      showToast(msg, 'error');
    }
  };

  // Route & Tenant Change Listener
  useEffect(() => {
    if (!token || !activeOrg) return;
    if (activeTab === 'overview') loadOverview();
    else if (activeTab === 'transactions') loadTransactions();
    else if (activeTab === 'upi') loadUpi();
    else if (activeTab === 'staff') loadStaff();
    else if (activeTab === 'accounts') loadAccounts();
    else if (activeTab === 'tables') loadTables();
    else if (activeTab === 'health') loadHealth();
  }, [token, activeOrg, activeTab]);

  useEffect(() => {
    if (activeTab === 'tables' && selectedTable) {
      loadTableData();
    }
  }, [activeTab, selectedTable, tableSearch, tableOffset]);

  // Real-Time Live Sync (every 5 seconds when visible)
  useEffect(() => {
    if (!token || !activeOrg) return;
    let isMounted = true;
    const interval = setInterval(async () => {
      if (document.visibilityState !== 'visible') return;
      try {
        setIsSyncing(true);
        if (activeTab === 'overview') await loadOverview();
        else if (activeTab === 'transactions') await loadTransactions(true);
        else if (activeTab === 'health') await loadHealth();
      } catch {
      } finally {
        if (isMounted) setIsSyncing(false);
      }
    }, 5000);
    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, [token, activeOrg, activeTab]);

  // Window Focus Auto-Sync
  useEffect(() => {
    const onFocus = () => {
      if (token && activeOrg) {
        if (activeTab === 'overview') loadOverview();
        else if (activeTab === 'transactions') loadTransactions(true);
        else if (activeTab === 'health') loadHealth();
      }
    };
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [token, activeOrg, activeTab]);

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
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/upi/${upi.id}`, { method: 'DELETE' });
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

  const handleSelectStaffAction = (_action: string, member: StaffMember | StaffInvite) => {
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
            if (!confirm(`Remove ${staffMember.fullName || staffMember.name || staffMember.mobileNumber} from organization?`)) return;
            try {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/staff/${staffMember.id}`, { method: 'DELETE' });
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

  const handleSelectTableRowAction = (row: Record<string, unknown>, table: string, columns: TableColumnDef[]) => {
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

  // If unauthenticated, show Google Login Page
  if (!token) {
    return (
      <LoginPage
        onLoginSuccess={handleLoginSuccess}
        theme={theme}
        onToggleTheme={() => setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'))}
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
        onLogout={handleLogout}
        theme={theme}
        onToggleTheme={() => setTheme((prev) => (prev === 'dark' ? 'light' : 'dark'))}
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
                  />
                }
              />

              <Route
                path="/upi"
                element={
                  <UpiPage
                    upiAccounts={upiAccounts}
                    onOpenNewUpi={() => setShowUpiModal({})}
                    onSelectUpiAction={handleSelectUpiAction}
                  />
                }
              />

              <Route
                path="/staff"
                element={
                  <StaffPage
                    staffList={staffList}
                    invitesList={invitesList}
                    onOpenInviteStaff={() => setShowStaffModal({})}
                    onSelectStaffAction={handleSelectStaffAction}
                  />
                }
              />

              <Route
                path="/accounts"
                element={
                  <AccountsPage
                    bankAccounts={bankAccounts}
                    onOpenNewBank={() => setShowNewBankModal(true)}
                  />
                }
              />

              <Route
                path="/orgs"
                element={
                  <ProfilePage
                    activeOrg={activeOrg}
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
                path="*"
                element={
                  <OverviewPage
                    stats={dashboardStats}
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
      />

      <BottomSheet
        isOpen={Boolean(bottomSheetConfig)}
        onClose={() => setBottomSheetConfig(null)}
        title={bottomSheetConfig?.title || 'Options'}
        subtitle={bottomSheetConfig?.subtitle}
        actions={bottomSheetConfig?.actions || []}
      />

      {/* Extracted Modular Modals */}
      <RecordPaymentModal
        isOpen={showNewTxnModal}
        onClose={() => setShowNewTxnModal(false)}
        activeOrg={activeOrg}
        defaultVpa={upiAccounts[0]?.vpa || `${activeOrg?.id}@upieasy`}
        onSubmitPayment={async (e) => {
          e.preventDefault();
          if (!activeOrg) return;
          const form = e.target as HTMLFormElement & {
            amount: HTMLInputElement;
            payerName: HTMLInputElement;
            payerVpa: HTMLInputElement;
            note: HTMLInputElement;
          };
          try {
            await apiFetch(`/api/v1/organizations/${activeOrg.id}/transactions`, {
              method: 'POST',
              body: JSON.stringify({
                amount: parseFloat(form.amount.value),
                payerName: form.payerName.value || 'Customer',
                payerVpa: form.payerVpa.value || 'customer@upi',
                payeeName: activeOrg.name,
                payeeVpa: upiAccounts[0]?.vpa || `${activeOrg.id}@upieasy`,
                note: form.note.value || 'Counter Sale',
                direction: 'RECEIVED',
                source: 'UPI_INTENT',
              }),
            });
            showToast('Payment recorded successfully!');
            setShowNewTxnModal(false);
            loadOverview();
            if (activeTab === 'transactions') loadTransactions();
          } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Error recording payment';
            showToast(msg, 'error');
          }
        }}
      />

      <UpiAccountModal
        isOpen={Boolean(showUpiModal)}
        onClose={() => setShowUpiModal(null)}
        initialData={(showUpiModal as UpiAccount)?.id ? (showUpiModal as UpiAccount) : null}
        activeOrg={activeOrg}
        onSubmitUpi={async (e) => {
          e.preventDefault();
          if (!activeOrg) return;
          const form = e.target as HTMLFormElement & {
            vpa: HTMLInputElement;
            payeeName: HTMLInputElement;
            mcc: HTMLInputElement;
            isDefault: HTMLInputElement;
          };
          const upiAccountItem = showUpiModal as UpiAccount | null;
          const isEditing = Boolean(upiAccountItem?.id);
          const payload = {
            vpa: form.vpa.value.trim().toLowerCase(),
            payeeName: form.payeeName.value.trim(),
            merchantCategoryCode: form.mcc.value || '5411',
            isDefault: form.isDefault.checked,
          };
          try {
            if (isEditing && upiAccountItem) {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/upi/${upiAccountItem.id}`, {
                method: 'PATCH',
                body: JSON.stringify(payload),
              });
              showToast('UPI details updated!');
            } else {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/upi`, {
                method: 'POST',
                body: JSON.stringify(payload),
              });
              showToast('UPI Account added!');
            }
            setShowUpiModal(null);
            loadUpi();
          } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Error saving UPI';
            showToast(msg, 'error');
          }
        }}
      />

      <QrCodeModal
        upiAccount={showQrModal}
        onClose={() => setShowQrModal(null)}
        onCopyLink={(link) => {
          navigator.clipboard.writeText(link);
          showToast('Copied payment link to clipboard');
        }}
      />

      <StaffModal
        isOpen={Boolean(showStaffModal)}
        onClose={() => setShowStaffModal(null)}
        initialData={(showStaffModal as StaffMember)?.id ? (showStaffModal as StaffMember) : null}
        onSubmitStaff={async (e) => {
          e.preventDefault();
          if (!activeOrg) return;
          const form = e.target as HTMLFormElement & {
            mobile: HTMLInputElement;
            name: HTMLInputElement;
            email: HTMLInputElement;
            role: HTMLSelectElement;
            status?: HTMLSelectElement;
          };
          const staffMemberItem = showStaffModal as StaffMember | null;
          const isEditing = Boolean(staffMemberItem?.id);
          try {
            if (isEditing && staffMemberItem) {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/staff/${staffMemberItem.id}`, {
                method: 'PATCH',
                body: JSON.stringify({ role: form.role.value, status: form.status?.value || 'ACTIVE' }),
              });
              showToast('Staff updated successfully!');
            } else {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/staff/invite`, {
                method: 'POST',
                body: JSON.stringify({
                  mobileNumber: form.mobile.value.trim(),
                  name: form.name.value.trim(),
                  email: form.email.value.trim() || undefined,
                  role: form.role.value,
                }),
              });
              showToast('Staff member added successfully!');
            }
            setShowStaffModal(null);
            loadStaff();
          } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Error saving staff';
            showToast(msg, 'error');
          }
        }}
      />

      <BankAccountModal
        isOpen={showNewBankModal}
        onClose={() => setShowNewBankModal(false)}
        activeOrg={activeOrg}
        onSubmitBank={async (e) => {
          e.preventDefault();
          if (!activeOrg) return;
          const form = e.target as HTMLFormElement & {
            bankName: HTMLInputElement;
            holderName: HTMLInputElement;
            accountNumber: HTMLInputElement;
            ifsc: HTMLInputElement;
            type: HTMLSelectElement;
          };
          try {
            await apiFetch(`/api/v1/organizations/${activeOrg.id}/accounts`, {
              method: 'POST',
              body: JSON.stringify({
                bankName: form.bankName.value.trim(),
                accountHolderName: form.holderName.value.trim(),
                accountNumber: form.accountNumber.value.trim(),
                ifscCode: form.ifsc.value.trim().toUpperCase(),
                accountType: form.type.value,
              }),
            });
            showToast('Bank account linked!');
            setShowNewBankModal(false);
            loadAccounts();
          } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Error linking bank account';
            showToast(msg, 'error');
          }
        }}
      />

      <TableRowModal
        isOpen={Boolean(showTableRowModal)}
        onClose={() => setShowTableRowModal(null)}
        selectedTable={selectedTable}
        columns={tableData?.columns || []}
        initialRow={showTableRowModal?.mode === 'edit' ? showTableRowModal.row : null}
        onSubmitRow={async (e) => {
          e.preventDefault();
          const form = e.target as HTMLFormElement;
          const isEditing = showTableRowModal?.mode === 'edit';
          try {
            if (isEditing && showTableRowModal?.row) {
              const updates: Record<string, string> = {};
              tableData.columns.forEach((col) => {
                const element = form.elements.namedItem(col.name) as HTMLInputElement | null;
                if (!col.isPrimary && element) updates[col.name] = element.value;
              });
              const pkCol = tableData.columns.find((c) => c.isPrimary)?.name || 'id';
              const pkVal = showTableRowModal.row[pkCol];
              await apiFetch(`/api/v1/admin/tables/${selectedTable}/${pkVal}`, {
                method: 'PATCH',
                body: JSON.stringify(updates),
              });
              showToast('Record updated successfully!');
            } else {
              const newRow: Record<string, string> = {};
              tableData.columns.forEach((col) => {
                const element = form.elements.namedItem(col.name) as HTMLInputElement | null;
                if (element && element.value !== '') newRow[col.name] = element.value;
              });
              await apiFetch(`/api/v1/admin/tables/${selectedTable}`, {
                method: 'POST',
                body: JSON.stringify(newRow),
              });
              showToast('Record inserted successfully!');
            }
            setShowTableRowModal(null);
            loadTables();
          } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Error saving record';
            showToast(msg, 'error');
          }
        }}
      />

      <TransactionDetailModal
        transaction={inspectTxn}
        onClose={() => setInspectTxn(null)}
      />

      {/* Global Notification Toast */}
      {toast && (
        <div className="fixed bottom-20 md:bottom-6 right-6 z-50 flex items-center gap-3 px-4 py-3 rounded-xl border border-border bg-card shadow-2xl animate-fade-in text-xs font-semibold text-card-foreground">
          <div
            className={`w-2 h-2 rounded-full ${
              toast.type === 'error'
                ? 'bg-destructive'
                : toast.type === 'info'
                ? 'bg-blue-500'
                : 'bg-emerald-500'
            }`}></div>
          <span>{toast.message}</span>
          <button
            onClick={() => setToast(null)}
            className="ml-2 text-muted-foreground hover:text-foreground">
            ✕
          </button>
        </div>
      )}
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
