import { db } from "./index.js";
import * as schema from "./schema/index.js";
import { generateId } from "../lib/crypto.js";
import { logger } from "../lib/logger.js";
import { eq } from "drizzle-orm";

export function seedDemoMerchantData(database = db) {
  try {
    const existingOrg = database
      .select()
      .from(schema.organizations)
      .where(eq(schema.organizations.id, "org_demo_store"))
      .get();

    if (existingOrg) {
      logger.info("[Seed] Demo merchant data already exists (org_demo_store). Skipping seed.");
      return;
    }

    logger.info("[Seed] Seeding demo merchant data and sample transactions...");

    const now = new Date();
    const dayMs = 24 * 60 * 60 * 1000;

    // 1. Merchant Owner User
    const merchantUser = {
      id: "usr_merchant_demo",
      googleId: "google_demo_merchant_101",
      mobileNumber: "9876543210",
      fullName: "Rajesh Sharma",
      email: "merchant@upieasy.com",
      avatarUrl: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150",
      status: "ACTIVE" as const,
      createdAt: new Date(now.getTime() - 14 * dayMs),
      updatedAt: now,
    };
    database.insert(schema.users).values(merchantUser).onConflictDoNothing().run();

    // 2. Cashier Staff User
    const cashierUser = {
      id: "usr_cashier_demo",
      googleId: null,
      mobileNumber: "9811122233",
      fullName: "Amit Kumar",
      email: "cashier@upieasy.com",
      avatarUrl: null,
      status: "ACTIVE" as const,
      createdAt: new Date(now.getTime() - 10 * dayMs),
      updatedAt: now,
    };
    database.insert(schema.users).values(cashierUser).onConflictDoNothing().run();

    // Active demo session
    database.insert(schema.sessions).values({
      id: "sess_demo_1",
      userId: merchantUser.id,
      deviceId: null,
      expiresAt: new Date(now.getTime() + 30 * dayMs),
      isRevoked: false,
      createdAt: now,
      updatedAt: now,
    }).onConflictDoNothing().run();

    // 3. Organization
    const demoOrg = {
      id: "org_demo_store",
      name: "Sharma Kirana & General Store",
      legalBusinessName: "Sharma Retail Enterprises LLP",
      category: "RETAIL",
      panNumber: "AAECS9821K",
      gstin: "27AAACS1429B1Z8",
      status: "ACTIVE" as const,
      ownerId: merchantUser.id,
      createdAt: new Date(now.getTime() - 14 * dayMs),
      updatedAt: now,
    };
    database.insert(schema.organizations).values(demoOrg).onConflictDoNothing().run();

    // 4. Memberships
    database.insert(schema.organizationMembers).values([
      {
        id: "mem_demo_owner",
        organizationId: demoOrg.id,
        userId: merchantUser.id,
        roleId: "role_owner",
        status: "ACTIVE" as const,
        joinedAt: new Date(now.getTime() - 14 * dayMs),
        createdAt: new Date(now.getTime() - 14 * dayMs),
        updatedAt: now,
      },
      {
        id: "mem_demo_cashier",
        organizationId: demoOrg.id,
        userId: cashierUser.id,
        roleId: "role_cashier",
        status: "ACTIVE" as const,
        invitedBy: merchantUser.id,
        joinedAt: new Date(now.getTime() - 10 * dayMs),
        createdAt: new Date(now.getTime() - 10 * dayMs),
        updatedAt: now,
      }
    ]).onConflictDoNothing().run();

    // 5. Notification Preferences
    database.insert(schema.notificationPreferences).values({
      id: "notif_pref_demo",
      userId: merchantUser.id,
      organizationId: demoOrg.id,
      paymentAlerts: true,
      staffAlerts: true,
      securityAlerts: true,
      voiceAnnouncements: true,
      updatedAt: now,
    }).onConflictDoNothing().run();

    // 6. Bank Account
    const bankAccount = {
      id: "bank_demo_hdfc",
      organizationId: demoOrg.id,
      bankName: "HDFC Bank",
      accountHolderName: "Sharma Kirana Store",
      accountNumberMasked: "••••••••4921",
      ifscCode: "HDFC0001234",
      accountType: "CURRENT" as const,
      status: "ACTIVE" as const,
      createdAt: new Date(now.getTime() - 14 * dayMs),
      updatedAt: now,
    };
    database.insert(schema.bankAccounts).values(bankAccount).onConflictDoNothing().run();

    // 7. UPI Accounts
    const primaryUpi = {
      id: "upi_demo_hdfc",
      organizationId: demoOrg.id,
      bankAccountId: bankAccount.id,
      vpa: "sharma.store@okhdfcbank",
      payeeName: "Sharma Kirana Store",
      merchantCategoryCode: "5411",
      isDefault: true,
      status: "ACTIVE" as const,
      transactionCount: 11,
      createdAt: new Date(now.getTime() - 14 * dayMs),
      updatedAt: now,
    };

    const secondaryUpi = {
      id: "upi_demo_paytm",
      organizationId: demoOrg.id,
      bankAccountId: bankAccount.id,
      vpa: "rajesh.sharma@paytm",
      payeeName: "Rajesh Sharma",
      merchantCategoryCode: "5411",
      isDefault: false,
      status: "ACTIVE" as const,
      transactionCount: 3,
      createdAt: new Date(now.getTime() - 7 * dayMs),
      updatedAt: now,
    };

    database.insert(schema.upiAccounts).values([primaryUpi, secondaryUpi]).onConflictDoNothing().run();

    // 8. Static QR Codes
    const primaryQrPayload = "upi://pay?pa=sharma.store@okhdfcbank&pn=Sharma+Kirana+Store&mc=5411&cu=INR";
    database.insert(schema.qrCodes).values([
      {
        id: "qr_demo_primary",
        organizationId: demoOrg.id,
        upiAccountId: primaryUpi.id,
        title: "Counter 1 - Main Checkout QR",
        qrPayload: primaryQrPayload,
        type: "STATIC",
        amount: null,
        note: "Store Payment",
        usageCount: 42,
        isActive: true,
        createdAt: new Date(now.getTime() - 14 * dayMs),
      },
      {
        id: "qr_demo_secondary",
        organizationId: demoOrg.id,
        upiAccountId: secondaryUpi.id,
        title: "Counter 2 - Express Billing QR",
        qrPayload: "upi://pay?pa=rajesh.sharma@paytm&pn=Rajesh+Sharma&mc=5411&cu=INR",
        type: "STATIC",
        amount: null,
        note: "Express Checkout",
        usageCount: 15,
        isActive: true,
        createdAt: new Date(now.getTime() - 7 * dayMs),
      }
    ]).onConflictDoNothing().run();

    // 9. Initial Outbox Events for Org and UPI
    database.insert(schema.outboxEvents).values([
      {
        id: "evt_init_org",
        organizationId: demoOrg.id,
        eventType: "organization.created",
        payloadJson: JSON.stringify({ organizationId: demoOrg.id, name: demoOrg.name }),
        status: "PROCESSED",
        createdAt: new Date(now.getTime() - 14 * dayMs),
      },
      {
        id: "evt_init_upi_1",
        organizationId: demoOrg.id,
        eventType: "upi.created",
        payloadJson: JSON.stringify(primaryUpi),
        status: "PROCESSED",
        createdAt: new Date(now.getTime() - 14 * dayMs),
      },
      {
        id: "evt_init_upi_2",
        organizationId: demoOrg.id,
        eventType: "upi.created",
        payloadJson: JSON.stringify(secondaryUpi),
        status: "PROCESSED",
        createdAt: new Date(now.getTime() - 7 * dayMs),
      }
    ]).onConflictDoNothing().run();

    // 10. Sample Transactions Dataset (Realistic Indian retail payments)
    const txData = [
      {
        id: "txn_demo_001",
        upiAccountId: primaryUpi.id,
        amount: 450.0,
        status: "SUCCESS",
        direction: "RECEIVED",
        payerName: "Pooja Verma",
        payerVpa: "pooja.v@oksbi",
        note: "Daily Dairy & Bakery",
        rrn: "428192019482",
        source: "QR_CODE",
        offsetHours: 2,
      },
      {
        id: "txn_demo_002",
        upiAccountId: primaryUpi.id,
        amount: 1250.0,
        status: "SUCCESS",
        direction: "RECEIVED",
        payerName: "Vikram Malhotra",
        payerVpa: "vikram.m@paytm",
        note: "Monthly Grocery Provisions",
        rrn: "428192084729",
        source: "QR_CODE",
        offsetHours: 5,
      },
      {
        id: "txn_demo_003",
        upiAccountId: primaryUpi.id,
        amount: 85.0,
        status: "SUCCESS",
        direction: "RECEIVED",
        payerName: "Anand K",
        payerVpa: "anand.k@icici",
        note: "Cold Drinks & Snacks",
        rrn: "428192193847",
        source: "QR_CODE",
        offsetHours: 8,
      },
      {
        id: "txn_demo_004",
        upiAccountId: primaryUpi.id,
        amount: 2400.0,
        status: "PENDING",
        direction: "RECEIVED",
        payerName: "Sunil Gupta",
        payerVpa: "sunil.gupta@axl",
        note: "Bulk Catering Supplies",
        rrn: "428192348572",
        source: "UPI_INTENT",
        offsetHours: 12,
      },
      {
        id: "txn_demo_005",
        upiAccountId: secondaryUpi.id,
        amount: 650.0,
        status: "FAILED",
        direction: "RECEIVED",
        payerName: "Deepa Nair",
        payerVpa: "deepa.nair@okhdfcbank",
        note: "Edible Oil & Spices (Bank Timeout)",
        rrn: "428192485921",
        source: "QR_CODE",
        offsetHours: 18,
      },
      {
        id: "txn_demo_006",
        upiAccountId: primaryUpi.id,
        amount: 180.0,
        status: "REFUNDED",
        direction: "SENT",
        payerName: "Rohit Jain",
        payerVpa: "rohit.j@upi",
        note: "Refund: Damaged biscuit carton",
        rrn: "428192592013",
        source: "UPI_INTENT",
        offsetHours: 24,
      },
      {
        id: "txn_demo_007",
        upiAccountId: primaryUpi.id,
        amount: 320.0,
        status: "SUCCESS",
        direction: "RECEIVED",
        payerName: "Kavita Rao",
        payerVpa: "kavita.rao@ybl",
        note: "Fresh Vegetables",
        rrn: "428192681029",
        source: "QR_CODE",
        offsetHours: 32,
      },
      {
        id: "txn_demo_008",
        upiAccountId: secondaryUpi.id,
        amount: 5400.0,
        status: "SUCCESS",
        direction: "RECEIVED",
        payerName: "Mehta Wholesalers",
        payerVpa: "mehta.enterprises@kotak",
        note: "Rice & Wheat 25kg Sacks",
        rrn: "428192792831",
        source: "UPI_INTENT",
        offsetHours: 48,
      },
      {
        id: "txn_demo_009",
        upiAccountId: primaryUpi.id,
        amount: 95.0,
        status: "SUCCESS",
        direction: "RECEIVED",
        payerName: "Sanjay Mishra",
        payerVpa: "sanjay.m@okaxis",
        note: "Packaged Water & Ice cream",
        rrn: "428192891724",
        source: "QR_CODE",
        offsetHours: 60,
      },
      {
        id: "txn_demo_010",
        upiAccountId: primaryUpi.id,
        amount: 1850.0,
        status: "SUCCESS",
        direction: "RECEIVED",
        payerName: "Priya Sharma",
        payerVpa: "priya.s@okhdfcbank",
        note: "Monthly Cleaning & Hygiene Pack",
        rrn: "428192994812",
        source: "QR_CODE",
        offsetHours: 72,
      },
      {
        id: "txn_demo_011",
        upiAccountId: primaryUpi.id,
        amount: 45.0,
        status: "SUCCESS",
        direction: "RECEIVED",
        payerName: "Arun Das",
        payerVpa: "arun.das@paytm",
        note: "Matchbox & Tea packet",
        rrn: "428193091823",
        source: "QR_CODE",
        offsetHours: 96,
      },
      {
        id: "txn_demo_012",
        upiAccountId: primaryUpi.id,
        amount: 3200.0,
        status: "SUCCESS",
        direction: "RECEIVED",
        payerName: "Rakesh Verma",
        payerVpa: "rakesh.v@icici",
        note: "Festival Sweets & Dryfruits box",
        rrn: "428193192834",
        source: "QR_CODE",
        offsetHours: 120,
      },
    ];

    for (const item of txData) {
      const txTime = new Date(now.getTime() - item.offsetHours * 60 * 60 * 1000);
      const isPrimary = item.upiAccountId === primaryUpi.id;
      const payeeName = isPrimary ? primaryUpi.payeeName : secondaryUpi.payeeName;
      const payeeVpa = isPrimary ? primaryUpi.vpa : secondaryUpi.vpa;

      database.insert(schema.transactions).values({
        id: item.id,
        organizationId: demoOrg.id,
        bankAccountId: bankAccount.id,
        upiAccountId: item.upiAccountId,
        type: item.status === "REFUNDED" ? "REFUND" : "PAYMENT",
        direction: item.direction as any,
        amount: item.amount,
        currency: "INR",
        status: item.status as any,
        paymentMethod: "UPI",
        provider: "NPCI",
        providerTransactionId: `npci_${item.rrn}`,
        upiTransactionId: `upi_${item.rrn}`,
        referenceNumber: item.rrn,
        payerName: item.payerName,
        payerVpa: item.payerVpa,
        payeeName,
        payeeVpa,
        note: item.note,
        staffId: cashierUser.id,
        source: item.source as any,
        occurredAt: txTime,
        createdAt: txTime,
        updatedAt: txTime,
      }).onConflictDoNothing().run();

      // Transaction Event
      database.insert(schema.transactionEvents).values({
        id: `txnev_${item.id}`,
        transactionId: item.id,
        organizationId: demoOrg.id,
        eventType: item.status === "SUCCESS" ? "transaction.success" : (item.status === "FAILED" ? "transaction.failed" : "transaction.created"),
        previousStatus: item.status === "SUCCESS" ? "PENDING" : null,
        newStatus: item.status,
        payloadJson: JSON.stringify({ rrn: item.rrn, amount: item.amount }),
        createdAt: txTime,
      }).onConflictDoNothing().run();

      // Transaction Reference
      database.insert(schema.transactionReferences).values({
        id: `ref_${item.id}`,
        transactionId: item.id,
        referenceType: "RRN",
        referenceNumber: item.rrn,
        createdAt: txTime,
      }).onConflictDoNothing().run();

      // Outbox Event for Delta-Sync
      database.insert(schema.outboxEvents).values({
        id: `evt_${item.id}`,
        organizationId: demoOrg.id,
        eventType: "transaction.created",
        payloadJson: JSON.stringify({
          id: item.id,
          organizationId: demoOrg.id,
          bankAccountId: bankAccount.id,
          upiAccountId: item.upiAccountId,
          type: item.status === "REFUNDED" ? "REFUND" : "PAYMENT",
          direction: item.direction,
          amount: item.amount,
          currency: "INR",
          status: item.status,
          paymentMethod: "UPI",
          referenceNumber: item.rrn,
          payerName: item.payerName,
          payerVpa: item.payerVpa,
          payeeName,
          payeeVpa,
          note: item.note,
          occurredAt: txTime.getTime(),
          createdAt: txTime.getTime(),
        }),
        status: "PROCESSED",
        createdAt: txTime,
      }).onConflictDoNothing().run();
    }

    logger.info("[Seed] Successfully seeded demo merchant dataset with 12 transactions and outbox events.");
  } catch (err: any) {
    logger.error({ err: err?.message || err }, "[Seed] Error seeding demo data");
  }
}

// Standalone execution if run directly via tsx
if (process.argv[1]?.includes("seed")) {
  import("./index.js").then(({ initDatabase }) => {
    initDatabase();
    seedDemoMerchantData();
    console.log("Seed finished successfully.");
    process.exit(0);
  });
}
