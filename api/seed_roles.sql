-- Seed standard permissions
INSERT OR IGNORE INTO permissions (id, name, description, category) VALUES
  ('perm_tx_read', 'transactions.read', 'View transactions', 'transactions'),
  ('perm_tx_export', 'transactions.export', 'Export transactions to CSV/Excel', 'transactions'),
  ('perm_tx_create', 'transactions.create', 'Create transactions', 'transactions'),
  ('perm_tx_refund', 'transactions.refund', 'Initiate refunds', 'transactions'),
  ('perm_evt_ingest', 'payment_events.ingest', 'Ingest observed payment events', 'transactions'),
  ('perm_acc_read', 'accounts.read', 'View bank accounts', 'accounts'),
  ('perm_acc_manage', 'accounts.manage', 'Manage bank accounts', 'accounts'),
  ('perm_upi_read', 'upi.read', 'View UPI IDs', 'upi'),
  ('perm_upi_manage', 'upi.manage', 'Manage UPI IDs', 'upi'),
  ('perm_qr_create', 'qr.create', 'Generate QR codes', 'qr'),
  ('perm_staff_read', 'staff.read', 'View staff members', 'staff'),
  ('perm_staff_manage', 'staff.manage', 'Manage staff members', 'staff'),
  ('perm_rep_read', 'reports.read', 'View reports', 'reports'),
  ('perm_org_manage', 'organization.manage', 'Manage organization settings', 'organization');

-- Seed standard roles
INSERT OR IGNORE INTO roles (id, name, description, is_system) VALUES
  ('role_owner', 'OWNER', 'Full business control', 1),
  ('role_manager', 'MANAGER', 'Business and staff operations', 1),
  ('role_cashier', 'CASHIER', 'Payment initiation and transaction records', 1),
  ('role_accountant', 'ACCOUNTANT', 'Reconciliation, reporting and exports', 1);

-- Seed role_permissions
-- OWNER
INSERT OR IGNORE INTO role_permissions (id, role_id, permission_id) VALUES
  ('role_owner_perm_tx_read', 'role_owner', 'perm_tx_read'),
  ('role_owner_perm_tx_export', 'role_owner', 'perm_tx_export'),
  ('role_owner_perm_tx_create', 'role_owner', 'perm_tx_create'),
  ('role_owner_perm_tx_refund', 'role_owner', 'perm_tx_refund'),
  ('role_owner_perm_evt_ingest', 'role_owner', 'perm_evt_ingest'),
  ('role_owner_perm_acc_read', 'role_owner', 'perm_acc_read'),
  ('role_owner_perm_acc_manage', 'role_owner', 'perm_acc_manage'),
  ('role_owner_perm_upi_read', 'role_owner', 'perm_upi_read'),
  ('role_owner_perm_upi_manage', 'role_owner', 'perm_upi_manage'),
  ('role_owner_perm_qr_create', 'role_owner', 'perm_qr_create'),
  ('role_owner_perm_staff_read', 'role_owner', 'perm_staff_read'),
  ('role_owner_perm_staff_manage', 'role_owner', 'perm_staff_manage'),
  ('role_owner_perm_rep_read', 'role_owner', 'perm_rep_read'),
  ('role_owner_perm_org_manage', 'role_owner', 'perm_org_manage');

-- MANAGER
INSERT OR IGNORE INTO role_permissions (id, role_id, permission_id) VALUES
  ('role_manager_perm_tx_read', 'role_manager', 'perm_tx_read'),
  ('role_manager_perm_tx_export', 'role_manager', 'perm_tx_export'),
  ('role_manager_perm_tx_create', 'role_manager', 'perm_tx_create'),
  ('role_manager_perm_evt_ingest', 'role_manager', 'perm_evt_ingest'),
  ('role_manager_perm_acc_read', 'role_manager', 'perm_acc_read'),
  ('role_manager_perm_upi_read', 'role_manager', 'perm_upi_read'),
  ('role_manager_perm_qr_create', 'role_manager', 'perm_qr_create'),
  ('role_manager_perm_staff_read', 'role_manager', 'perm_staff_read'),
  ('role_manager_perm_staff_manage', 'role_manager', 'perm_staff_manage'),
  ('role_manager_perm_rep_read', 'role_manager', 'perm_rep_read');

-- CASHIER
INSERT OR IGNORE INTO role_permissions (id, role_id, permission_id) VALUES
  ('role_cashier_perm_tx_read', 'role_cashier', 'perm_tx_read'),
  ('role_cashier_perm_tx_create', 'role_cashier', 'perm_tx_create'),
  ('role_cashier_perm_evt_ingest', 'role_cashier', 'perm_evt_ingest'),
  ('role_cashier_perm_upi_read', 'role_cashier', 'perm_upi_read'),
  ('role_cashier_perm_qr_create', 'role_cashier', 'perm_qr_create');

-- ACCOUNTANT
INSERT OR IGNORE INTO role_permissions (id, role_id, permission_id) VALUES
  ('role_accountant_perm_tx_read', 'role_accountant', 'perm_tx_read'),
  ('role_accountant_perm_tx_export', 'role_accountant', 'perm_tx_export'),
  ('role_accountant_perm_rep_read', 'role_accountant', 'perm_rep_read'),
  ('role_accountant_perm_acc_read', 'role_accountant', 'perm_acc_read'),
  ('role_accountant_perm_upi_read', 'role_accountant', 'perm_upi_read');
