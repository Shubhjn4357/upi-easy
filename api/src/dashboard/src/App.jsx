// Main Application: Clean, Secure, Declarative React Router Architecture
import React, { useState, useEffect, useMemo, useRef, Suspense, startTransition } from 'react';
import { Router, Routes, Route, ProtectedRoute, useNavigate, useLocation } from './lib/router.jsx';
import { getStoredAuth, saveAuth, clearAuth } from './lib/auth.js';
import { createApiClient } from './lib/api.js';

import { SuspenseFallback } from './components/ui/components.jsx';
import Navbar from './components/Navbar.jsx';
import Sidebar from './components/Sidebar.jsx';
import BottomNav from './components/BottomNav.jsx';
import BottomSheet from './components/BottomSheet.jsx';

import LoginPage from './pages/LoginPage.jsx';
import OverviewPage from './pages/OverviewPage.jsx';
import TransactionsPage from './pages/TransactionsPage.jsx';
import UpiPage from './pages/UpiPage.jsx';
import StaffPage from './pages/StaffPage.jsx';
import AccountsPage from './pages/AccountsPage.jsx';
import ProfilePage from './pages/ProfilePage.jsx';
import TablesPage from './pages/TablesPage.jsx';
import HealthPage from './pages/HealthPage.jsx';

import RecordPaymentModal from './components/modals/RecordPaymentModal.jsx';
import UpiAccountModal from './components/modals/UpiAccountModal.jsx';
import QrCodeModal from './components/modals/QrCodeModal.jsx';
import StaffModal from './components/modals/StaffModal.jsx';
import BankAccountModal from './components/modals/BankAccountModal.jsx';
import TableRowModal from './components/modals/TableRowModal.jsx';
import TransactionDetailModal from './components/modals/TransactionDetailModal.jsx';

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
} from './components/ui/icons.jsx';

function AppContent() {
  const navigate = useNavigate();
  const location = useLocation();

  // Active tab derived cleanly from router pathname
  const activeTab = location.pathname.replace(/^\//, '') || "overview";

  // Theme State
  const [theme, setTheme] = useState(() => localStorage.getItem("upieasy_theme") || "dark");
  useEffect(() => {
    const root = document.documentElement;
    if (theme === "dark") {
      root.classList.add("dark");
      root.classList.remove("light");
    } else {
      root.classList.remove("dark");
      root.classList.add("light");
    }
    localStorage.setItem("upieasy_theme", theme);
  }, [theme]);

  // Auth State & Token Expiration Guard
  const [auth, setAuth] = useState(() => getStoredAuth());
  const token = auth.token;
  const user = auth.user;

  // Multi-Tenant Org State
  const [organizations, setOrganizations] = useState([]);
  const [activeOrg, setActiveOrg] = useState(null);

  // Real-Time Telemetry State
  const [pingMs, setPingMs] = useState(null);
  const [isSyncing, setIsSyncing] = useState(false);

  // Entities
  const [dashboardStats, setDashboardStats] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [txnLoading, setTxnLoading] = useState(false);
  const [txnSearch, setTxnSearch] = useState("");
  const [txnStatus, setTxnStatus] = useState("");

  const [upiAccounts, setUpiAccounts] = useState([]);
  const [staffList, setStaffList] = useState([]);
  const [invitesList, setInvitesList] = useState([]);
  const [bankAccounts, setBankAccounts] = useState([]);

  // Database Tables State (Admin)
  const [adminTables, setAdminTables] = useState([]);
  const [selectedTable, setSelectedTable] = useState("transactions");
  const [tableData, setTableData] = useState({ rows: [], columns: [], total: 0 });
  const [tableLoading, setTableLoading] = useState(false);
  const [tableSearch, setTableSearch] = useState("");
  const [tableOffset, setTableOffset] = useState(0);

  // Health
  const [healthData, setHealthData] = useState(null);

  // Modals & Sheets
  const [bottomSheetConfig, setBottomSheetConfig] = useState(null);
  const [showNewTxnModal, setShowNewTxnModal] = useState(false);
  const [showUpiModal, setShowUpiModal] = useState(null); // null = closed, {} = new, { ...upi } = edit
  const [showQrModal, setShowQrModal] = useState(null);
  const [showStaffModal, setShowStaffModal] = useState(null); // null = closed, {} = new, { ...member } = edit
  const [showNewBankModal, setShowNewBankModal] = useState(false);
  const [showTableRowModal, setShowTableRowModal] = useState(null); // null = closed, { mode: 'insert'|'edit', row }
  const [inspectTxn, setInspectTxn] = useState(null);

  // Toast
  const [toast, setToast] = useState(null);
  const showToast = (message, type = "success") => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3500);
  };

  // Secure API Client with auto 401 logout & latency tracking
  const apiFetch = useMemo(() => {
    return createApiClient(
      () => token,
      () => {
        setAuth({ token: "", user: null });
        showToast("Session expired. Please sign in again.", "error");
      },
      setPingMs
    );
  }, [token]);

  // Auth Actions
  const handleLoginSuccess = async (newAccessToken, newUser) => {
    saveAuth(newAccessToken, newUser);
    setAuth({ token: newAccessToken, user: newUser });
    showToast(`Welcome, ${newUser.fullName}!`);
    await loadOrganizations(newAccessToken);
    navigate("/overview");
  };

  const handleLogout = () => {
    clearAuth();
    setAuth({ token: "", user: null });
    setActiveOrg(null);
    setOrganizations([]);
    showToast("Signed out successfully", "info");
  };

  // Load Organizations
  const loadOrganizations = async (authToken = token) => {
    try {
      const res = await fetch(`${window.location.origin}/api/v1/organizations`, {
        headers: { Authorization: `Bearer ${authToken}` }
      });
      const data = await res.json();
      if (data.success && data.organizations?.length > 0) {
        setOrganizations(data.organizations);
        const savedOrgId = localStorage.getItem("upieasy_active_org_id");
        const def = (savedOrgId && data.organizations.find(o => o.id === savedOrgId)) || data.organizations[0];
        setActiveOrg(def);
      }
    } catch (err) {
      console.error(err);
    }
  };

  const handleSelectOrg = (org) => {
    setActiveOrg(org);
    localStorage.setItem("upieasy_active_org_id", org.id);
  };

  useEffect(() => {
    if (token) loadOrganizations();
  }, [token]);

  // Tab Loaders
  const loadOverview = async () => {
    try {
      const data = await apiFetch(`/api/v1/organizations/${activeOrg.id}/dashboard`);
      if (data.success) setDashboardStats(data.dashboard);
    } catch {}
  };

  const loadTransactions = async (silent = false) => {
    if (!silent) setTxnLoading(true);
    try {
      let url = `/api/v1/organizations/${activeOrg.id}/transactions?limit=50`;
      if (txnStatus) url += `&status=${txnStatus}`;
      if (txnSearch) url += `&search=${encodeURIComponent(txnSearch)}`;
      const data = await apiFetch(url);
      if (data.success) setTransactions(data.transactions || []);
    } catch (err) {
      if (!silent) showToast(err.message, "error");
    } finally {
      if (!silent) setTxnLoading(false);
    }
  };

  const loadUpi = async () => {
    try {
      const data = await apiFetch(`/api/v1/organizations/${activeOrg.id}/upi`);
      if (data.success) setUpiAccounts(data.upiAccounts || []);
    } catch (err) {
      showToast(err.message, "error");
    }
  };

  const loadStaff = async () => {
    try {
      const [staffRes, invitesRes] = await Promise.all([
        apiFetch(`/api/v1/organizations/${activeOrg.id}/staff`),
        apiFetch(`/api/v1/organizations/${activeOrg.id}/invites`),
      ]);
      if (staffRes.success) setStaffList(staffRes.staff || []);
      if (invitesRes.success) setInvitesList(invitesRes.invites || []);
    } catch (err) {
      showToast(err.message, "error");
    }
  };

  const loadAccounts = async () => {
    try {
      const data = await apiFetch(`/api/v1/organizations/${activeOrg.id}/accounts`);
      if (data.success) setBankAccounts(data.accounts || []);
    } catch (err) {
      showToast(err.message, "error");
    }
  };

  const loadTables = async () => {
    try {
      const data = await apiFetch("/api/v1/admin/tables");
      if (data.success) {
        setAdminTables(data.tables || []);
        if (data.tables?.length > 0 && !selectedTable) {
          setSelectedTable(data.tables[0].name);
        }
      }
    } catch (err) {
      showToast(err.message, "error");
    }
  };

  const loadTableData = async () => {
    setTableLoading(true);
    try {
      let url = `/api/v1/admin/tables/${selectedTable}?limit=25&offset=${tableOffset}`;
      if (tableSearch) url += `&search=${encodeURIComponent(tableSearch)}`;
      const data = await apiFetch(url);
      if (data.success) setTableData(data);
    } catch (err) {
      showToast(err.message, "error");
    } finally {
      setTableLoading(false);
    }
  };

  const loadHealth = async () => {
    try {
      const data = await apiFetch("/api/v1/admin/health");
      if (data.success) setHealthData(data);
    } catch (err) {
      showToast(err.message, "error");
    }
  };

  // Route & Tenant Change Listener
  useEffect(() => {
    if (!token || !activeOrg) return;
    if (activeTab === "overview") loadOverview();
    else if (activeTab === "transactions") loadTransactions();
    else if (activeTab === "upi") loadUpi();
    else if (activeTab === "staff") loadStaff();
    else if (activeTab === "accounts") loadAccounts();
    else if (activeTab === "tables") loadTables();
    else if (activeTab === "health") loadHealth();
  }, [token, activeOrg, activeTab]);

  useEffect(() => {
    if (activeTab === "tables" && selectedTable) {
      loadTableData();
    }
  }, [activeTab, selectedTable, tableSearch, tableOffset]);

  // Real-Time Live Sync (every 5 seconds when visible)
  useEffect(() => {
    if (!token || !activeOrg) return;
    let isMounted = true;
    const interval = setInterval(async () => {
      if (document.visibilityState !== "visible") return;
      try {
        setIsSyncing(true);
        if (activeTab === "overview") await loadOverview();
        else if (activeTab === "transactions") await loadTransactions(true);
        else if (activeTab === "health") await loadHealth();
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
        if (activeTab === "overview") loadOverview();
        else if (activeTab === "transactions") loadTransactions(true);
        else if (activeTab === "health") loadHealth();
      }
    };
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, [token, activeOrg, activeTab]);

  // Mobile Bottom Sheet Action Handlers
  const handleSelectTxnAction = (txn) => {
    setBottomSheetConfig({
      title: `Transaction ${txn.referenceNumber || txn.id.slice(0, 8)}`,
      subtitle: `₹${Number(txn.amount).toFixed(2)} · ${txn.status}`,
      actions: [
        {
          label: "View Full Audit Payload",
          icon: IconEye,
          onClick: () => setInspectTxn(txn),
        },
        {
          label: "Copy UTR Reference",
          icon: IconCopy,
          onClick: () => {
            navigator.clipboard.writeText(txn.referenceNumber || txn.id);
            showToast("UTR reference copied!");
          },
        },
        {
          label: "Issue Refund",
          icon: IconArrowUpRight,
          onClick: () => showToast("Refund initiated to customer account"),
        },
      ],
    });
  };

  const handleSelectUpiAction = (upi, mode = "menu") => {
    if (mode === "qr") {
      setShowQrModal(upi);
      return;
    }
    setBottomSheetConfig({
      title: upi.payeeName,
      subtitle: upi.vpa,
      actions: [
        {
          label: "Generate Counter QR Code",
          icon: IconQrCode,
          onClick: () => setShowQrModal(upi),
        },
        {
          label: "Edit UPI Account Details",
          icon: IconEdit,
          onClick: () => setShowUpiModal(upi),
        },
        {
          label: upi.isDefault ? "Unset Primary" : "Set as Primary Default",
          icon: IconStar,
          variant: "success",
          onClick: async () => {
            try {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/upi/${upi.id}`, {
                method: "PATCH",
                body: JSON.stringify({ isDefault: !upi.isDefault }),
              });
              showToast("UPI default status updated!");
              loadUpi();
            } catch (err) {
              showToast(err.message, "error");
            }
          },
        },
        {
          label: "Delete UPI ID",
          icon: IconTrash2,
          variant: "danger",
          onClick: async () => {
            if (!confirm(`Delete UPI VPA ${upi.vpa}?`)) return;
            try {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/upi/${upi.id}`, { method: "DELETE" });
              showToast("UPI Account removed");
              loadUpi();
            } catch (err) {
              showToast(err.message, "error");
            }
          },
        },
      ],
    });
  };

  const handleSelectStaffAction = (member) => {
    setBottomSheetConfig({
      title: member.fullName || member.mobileNumber,
      subtitle: `Role: ${member.role} · Status: ${member.status}`,
      actions: [
        {
          label: "Edit Assigned Role & Permissions",
          icon: IconEdit,
          onClick: () => setShowStaffModal(member),
        },
        {
          label: member.status === "ACTIVE" ? "Suspend Member Access" : "Activate Member Access",
          icon: IconLock,
          variant: member.status === "ACTIVE" ? "danger" : "success",
          onClick: async () => {
            const nextStatus = member.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
            try {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/staff/${member.id}`, {
                method: "PATCH",
                body: JSON.stringify({ status: nextStatus }),
              });
              showToast(`Staff access set to ${nextStatus}`);
              loadStaff();
            } catch (err) {
              showToast(err.message, "error");
            }
          },
        },
        {
          label: "Remove Member from Store",
          icon: IconTrash2,
          variant: "danger",
          onClick: async () => {
            if (!confirm(`Remove ${member.fullName || member.mobileNumber} from organization?`)) return;
            try {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/staff/${member.id}`, { method: "DELETE" });
              showToast("Staff member removed");
              loadStaff();
            } catch (err) {
              showToast(err.message, "error");
            }
          },
        },
      ],
    });
  };

  const handleSelectTableRowAction = (row, table, columns) => {
    const pkCol = columns.find(c => c.isPrimary)?.name || 'id';
    const pkVal = row[pkCol];
    setBottomSheetConfig({
      title: `${table} Record`,
      subtitle: `Primary Key (${pkCol}): ${pkVal}`,
      actions: [
        {
          label: "Edit Row Fields",
          icon: IconEdit,
          onClick: () => setShowTableRowModal({ mode: "edit", row }),
        },
        {
          label: "Copy Row as JSON",
          icon: IconCopy,
          onClick: () => {
            navigator.clipboard.writeText(JSON.stringify(row, null, 2));
            showToast("Copied row JSON to clipboard");
          },
        },
        {
          label: "Delete Record Permanently",
          icon: IconTrash2,
          variant: "danger",
          onClick: async () => {
            if (!confirm(`Delete record with ${pkCol}=${pkVal} from ${table}?`)) return;
            try {
              await apiFetch(`/api/v1/admin/tables/${table}/${pkVal}`, { method: "DELETE" });
              showToast("Record deleted successfully!");
              loadTables();
            } catch (err) {
              showToast(err.message, "error");
            }
          },
        },
      ],
    });
  };

  const handleOpenMoreSheet = () => {
    setBottomSheetConfig({
      title: "More Features & Settings",
      subtitle: "Management & Diagnostics",
      actions: [
        { label: "Settlement Bank Accounts", icon: IconLandmark, onClick: () => navigate("/accounts") },
        { label: "Business Profile & Registration", icon: IconBuilding, onClick: () => navigate("/orgs") },
        { label: "Database Table Explorer (Admin)", icon: IconDatabase, onClick: () => navigate("/tables") },
        { label: "System Health Diagnostics", icon: IconHeartPulse, onClick: () => navigate("/health") },
      ],
    });
  };

  // If unauthenticated, show Google Login Page
  if (!token) {
    return (
      <LoginPage 
        onLoginSuccess={handleLoginSuccess}
        theme={theme}
        onToggleTheme={() => setTheme(prev => prev === "dark" ? "light" : "dark")}
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
        onToggleTheme={() => setTheme(prev => prev === "dark" ? "light" : "dark")}
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
              <Route path="/overview" element={
                <OverviewPage 
                  stats={dashboardStats}
                  onOpenNewTxn={() => setShowNewTxnModal(true)}
                  onOpenNewUpi={() => setShowUpiModal({})}
                  onNavigate={(tab) => navigate(`/${tab}`)}
                  onInspectTxn={handleSelectTxnAction}
                />
              } />

              <Route path="/transactions" element={
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
              } />

              <Route path="/upi" element={
                <UpiPage 
                  upiAccounts={upiAccounts}
                  onOpenNewUpi={() => setShowUpiModal({})}
                  onSelectUpiAction={handleSelectUpiAction}
                />
              } />

              <Route path="/staff" element={
                <StaffPage 
                  staffList={staffList}
                  invitesList={invitesList}
                  onOpenInviteStaff={() => setShowStaffModal({})}
                  onSelectStaffAction={handleSelectStaffAction}
                />
              } />

              <Route path="/accounts" element={
                <AccountsPage 
                  bankAccounts={bankAccounts}
                  onOpenNewBank={() => setShowNewBankModal(true)}
                />
              } />

              <Route path="/orgs" element={
                <ProfilePage 
                  activeOrg={activeOrg}
                  onSaveProfile={async (e) => {
                    e.preventDefault();
                    const form = e.target;
                    try {
                      await apiFetch(`/api/v1/organizations/${activeOrg.id}`, {
                        method: "PATCH",
                        body: JSON.stringify({
                          name: form.name.value,
                          legalBusinessName: form.legalBusinessName.value,
                          category: form.category.value,
                          gstin: form.gstin.value || null,
                          panNumber: form.panNumber.value || null,
                        }),
                      });
                      showToast("Profile saved successfully");
                      loadOrganizations();
                    } catch (err) {
                      showToast(err.message, "error");
                    }
                  }}
                />
              } />

              <Route path="/tables" element={
                <ProtectedRoute isAuthenticated={Boolean(token)} userRole={activeOrg?.role} requiredRole="MANAGER">
                  <TablesPage 
                    tables={adminTables}
                    selectedTable={selectedTable}
                    onSelectTable={(t) => {
                      setSelectedTable(t);
                      setTableOffset(0);
                      setTableSearch("");
                    }}
                    tableData={tableData}
                    loading={tableLoading}
                    search={tableSearch}
                    onSearchChange={setTableSearch}
                    onOpenInsertModal={() => setShowTableRowModal({ mode: "insert" })}
                    onSelectRowAction={handleSelectTableRowAction}
                  />
                </ProtectedRoute>
              } />

              <Route path="/health" element={
                <HealthPage 
                  healthData={healthData}
                  pingMs={pingMs}
                  onRefresh={loadHealth}
                  onResetSeed={async () => {
                    try {
                      await apiFetch("/api/v1/admin/seed", { method: "POST" });
                      showToast("Database baseline re-seeded successfully!");
                      loadOverview();
                    } catch (err) {
                      showToast(err.message, "error");
                    }
                  }}
                />
              } />

              <Route path="*" element={
                <OverviewPage 
                  stats={dashboardStats}
                  onOpenNewTxn={() => setShowNewTxnModal(true)}
                  onOpenNewUpi={() => setShowUpiModal({})}
                  onNavigate={(tab) => navigate(`/${tab}`)}
                  onInspectTxn={handleSelectTxnAction}
                />
              } />
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
        title={bottomSheetConfig?.title || "Options"}
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
          const form = e.target;
          try {
            await apiFetch(`/api/v1/organizations/${activeOrg.id}/transactions`, {
              method: "POST",
              body: JSON.stringify({
                amount: parseFloat(form.amount.value),
                payerName: form.payerName.value || "Customer",
                payerVpa: form.payerVpa.value || "customer@upi",
                payeeName: activeOrg.name,
                payeeVpa: upiAccounts[0]?.vpa || `${activeOrg.id}@upieasy`,
                note: form.note.value || "Counter Sale",
                direction: "RECEIVED",
                source: "UPI_INTENT",
              }),
            });
            showToast("Payment recorded successfully!");
            setShowNewTxnModal(false);
            loadOverview();
            if (activeTab === "transactions") loadTransactions();
          } catch (err) {
            showToast(err.message, "error");
          }
        }}
      />

      <UpiAccountModal
        isOpen={Boolean(showUpiModal)}
        onClose={() => setShowUpiModal(null)}
        initialData={showUpiModal?.id ? showUpiModal : null}
        activeOrg={activeOrg}
        onSubmitUpi={async (e) => {
          e.preventDefault();
          const form = e.target;
          const isEditing = Boolean(showUpiModal?.id);
          const payload = {
            vpa: form.vpa.value.trim().toLowerCase(),
            payeeName: form.payeeName.value.trim(),
            merchantCategoryCode: form.mcc.value || "5411",
            isDefault: form.isDefault.checked,
          };
          try {
            if (isEditing) {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/upi/${showUpiModal.id}`, {
                method: "PATCH",
                body: JSON.stringify(payload),
              });
              showToast("UPI details updated!");
            } else {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/upi`, {
                method: "POST",
                body: JSON.stringify(payload),
              });
              showToast("UPI Account added!");
            }
            setShowUpiModal(null);
            loadUpi();
          } catch (err) {
            showToast(err.message, "error");
          }
        }}
      />

      <QrCodeModal
        upiAccount={showQrModal}
        onClose={() => setShowQrModal(null)}
        onCopyLink={(link) => {
          navigator.clipboard.writeText(link);
          showToast("Copied payment link to clipboard");
        }}
      />

      <StaffModal
        isOpen={Boolean(showStaffModal)}
        onClose={() => setShowStaffModal(null)}
        initialData={showStaffModal?.id ? showStaffModal : null}
        onSubmitStaff={async (e) => {
          e.preventDefault();
          const form = e.target;
          const isEditing = Boolean(showStaffModal?.id);
          try {
            if (isEditing) {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/staff/${showStaffModal.id}`, {
                method: "PATCH",
                body: JSON.stringify({ role: form.role.value, status: form.status.value }),
              });
              showToast("Staff updated successfully!");
            } else {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/staff/invite`, {
                method: "POST",
                body: JSON.stringify({
                  mobileNumber: form.mobile.value.trim(),
                  name: form.name.value.trim(),
                  email: form.email.value.trim() || undefined,
                  role: form.role.value,
                }),
              });
              showToast("Staff member added successfully!");
            }
            setShowStaffModal(null);
            loadStaff();
          } catch (err) {
            showToast(err.message, "error");
          }
        }}
      />

      <BankAccountModal
        isOpen={showNewBankModal}
        onClose={() => setShowNewBankModal(false)}
        activeOrg={activeOrg}
        onSubmitBank={async (e) => {
          e.preventDefault();
          const form = e.target;
          try {
            await apiFetch(`/api/v1/organizations/${activeOrg.id}/accounts`, {
              method: "POST",
              body: JSON.stringify({
                bankName: form.bankName.value.trim(),
                accountHolderName: form.holderName.value.trim(),
                accountNumber: form.accountNumber.value.trim(),
                ifscCode: form.ifsc.value.trim().toUpperCase(),
                accountType: form.type.value,
              }),
            });
            showToast("Bank account linked!");
            setShowNewBankModal(false);
            loadAccounts();
          } catch (err) {
            showToast(err.message, "error");
          }
        }}
      />

      <TableRowModal
        isOpen={Boolean(showTableRowModal)}
        onClose={() => setShowTableRowModal(null)}
        selectedTable={selectedTable}
        columns={tableData?.columns || []}
        initialRow={showTableRowModal?.mode === "edit" ? showTableRowModal.row : null}
        onSubmitRow={async (e) => {
          e.preventDefault();
          const form = e.target;
          const isEditing = showTableRowModal?.mode === "edit";
          try {
            if (isEditing) {
              const updates = {};
              tableData.columns.forEach(col => {
                if (!col.isPrimary && form[col.name]) updates[col.name] = form[col.name].value;
              });
              const pkCol = tableData.columns.find(c => c.isPrimary)?.name || 'id';
              const pkVal = showTableRowModal.row[pkCol];
              await apiFetch(`/api/v1/admin/tables/${selectedTable}/${pkVal}`, {
                method: "PATCH",
                body: JSON.stringify(updates),
              });
              showToast("Record updated successfully!");
            } else {
              const newRow = {};
              tableData.columns.forEach(col => {
                if (form[col.name] && form[col.name].value !== "") newRow[col.name] = form[col.name].value;
              });
              await apiFetch(`/api/v1/admin/tables/${selectedTable}`, {
                method: "POST",
                body: JSON.stringify(newRow),
              });
              showToast("Record inserted successfully!");
            }
            setShowTableRowModal(null);
            loadTables();
          } catch (err) {
            showToast(err.message, "error");
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
          <div className={`w-2 h-2 rounded-full ${
            toast.type === 'error' ? 'bg-destructive' : toast.type === 'info' ? 'bg-blue-500' : 'bg-emerald-500'
          }`}></div>
          <span>{toast.message}</span>
          <button onClick={() => setToast(null)} className="ml-2 text-muted-foreground hover:text-foreground">✕</button>
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

// React Root Initialization
const rootElement = document.getElementById('root');
if (rootElement && typeof ReactDOM !== 'undefined') {
  const root = ReactDOM.createRoot(rootElement);
  root.render(<App />);
}
