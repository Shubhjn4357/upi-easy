import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and, desc, sql, like, or } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { requireAuth } from "../../middleware/auth.js";
import { requireTenant } from "../../middleware/tenant.js";
import { requirePermission } from "../../middleware/rbac.js";
import { handleIdempotency } from "../../middleware/idempotency.js";
import { NotFoundError, AppError } from "../../lib/errors.js";
import type { AppEnv } from "../../types/hono.js";

export const transactionsRouter = new Hono<AppEnv>();

transactionsRouter.use("*", requireAuth);

transactionsRouter.get(
  "/:orgId/transactions",
  requireTenant,
  requirePermission("transactions.read"),
  async (c) => {
    const orgId = c.get("organizationId");
    const query = c.req.query();

    const limit = Math.min(Number(query.limit) || 20, 100);
    const offset = Number(query.offset) || 0;
    const status = query.status;
    const direction = query.direction;
    const upiAccountId = query.upiAccountId;
    const search = query.search?.trim();

    const conditions = [eq(schema.transactions.organizationId, orgId)];

    if (status) {
      conditions.push(eq(schema.transactions.status, status as any));
    }
    if (direction) {
      conditions.push(eq(schema.transactions.direction, direction as any));
    }
    if (upiAccountId) {
      conditions.push(eq(schema.transactions.upiAccountId, upiAccountId));
    }
    if (search) {
      conditions.push(
        or(
          like(schema.transactions.referenceNumber, `%${search}%`),
          like(schema.transactions.payerName, `%${search}%`),
          like(schema.transactions.payerVpa, `%${search}%`),
          like(schema.transactions.note, `%${search}%`)
        )!
      );
    }

    const txns = db
      .select()
      .from(schema.transactions)
      .where(and(...conditions))
      .orderBy(desc(schema.transactions.occurredAt))
      .limit(limit)
      .offset(offset)
      .all();

    const totalCount = db
      .select({ count: sql<number>`count(*)` })
      .from(schema.transactions)
      .where(and(...conditions))
      .get()?.count ?? 0;

    return c.json({
      success: true,
      data: txns,
      pagination: {
        total: totalCount,
        limit,
        offset,
        hasMore: offset + txns.length < totalCount,
      },
    });
  }
);

transactionsRouter.get(
  "/:orgId/transactions/:id",
  requireTenant,
  requirePermission("transactions.read"),
  async (c) => {
    const orgId = c.get("organizationId");
    const id = c.req.param("id");

    const txn = db
      .select()
      .from(schema.transactions)
      .where(and(eq(schema.transactions.id, id), eq(schema.transactions.organizationId, orgId)))
      .get();

    if (!txn) {
      throw new NotFoundError("Transaction not found");
    }

    // Fetch related events and references
    const events = db
      .select()
      .from(schema.transactionEvents)
      .where(eq(schema.transactionEvents.transactionId, id))
      .orderBy(desc(schema.transactionEvents.createdAt))
      .all();

    const references = db
      .select()
      .from(schema.transactionReferences)
      .where(eq(schema.transactionReferences.transactionId, id))
      .all();

    return c.json({
      success: true,
      transaction: {
        ...txn,
        events,
        references,
      },
    });
  }
);

// Record payment initiation / collection record (Idempotent)
transactionsRouter.post(
  "/:orgId/transactions",
  requireTenant,
  requirePermission("transactions.create"),
  handleIdempotency,
  async (c) => {
    const orgId = c.get("organizationId");
    const actorId = c.get("userId");
    const body = await c.req.json();

    const validator = z.object({
      upiAccountId: z.string().optional(),
      bankAccountId: z.string().optional(),
      type: z.enum(["PAYMENT", "COLLECTION", "REFUND", "TRANSFER"]).default("PAYMENT"),
      direction: z.enum(["RECEIVED", "SENT"]).default("RECEIVED"),
      amount: z.number().positive("Amount must be greater than 0"),
      payerName: z.string().optional(),
      payerVpa: z.string().optional(),
      payeeName: z.string(),
      payeeVpa: z.string(),
      note: z.string().optional(),
      referenceNumber: z.string().optional(),
      source: z.enum(["UPI_INTENT", "QR_CODE", "STATEMENT", "BANK_API"]).default("UPI_INTENT"),
    });

    const data = validator.parse(body);
    const txnId = generateId("txn");
    const now = new Date();

    // Section 11 rule: Never create a SUCCESS transaction from a local assumption.
    // Local payment requests or intent initiations start as PENDING.
    const initialStatus = "PENDING";

    db.insert(schema.transactions)
      .values({
        id: txnId,
        organizationId: orgId,
        bankAccountId: data.bankAccountId ?? null,
        upiAccountId: data.upiAccountId ?? null,
        type: data.type,
        direction: data.direction,
        amount: data.amount,
        currency: "INR",
        status: initialStatus,
        paymentMethod: "UPI",
        provider: "NPCI",
        referenceNumber: data.referenceNumber ?? null,
        payerName: data.payerName ?? null,
        payerVpa: data.payerVpa ?? null,
        payeeName: data.payeeName,
        payeeVpa: data.payeeVpa,
        note: data.note ?? null,
        staffId: actorId,
        source: data.source,
        occurredAt: now,
        createdAt: now,
        updatedAt: now,
      })
      .run();

    // Record initial transaction event
    db.insert(schema.transactionEvents)
      .values({
        id: generateId("txnev"),
        transactionId: txnId,
        organizationId: orgId,
        eventType: "transaction.created",
        previousStatus: null,
        newStatus: initialStatus,
        payloadJson: JSON.stringify({ amount: data.amount, payeeVpa: data.payeeVpa }),
        createdAt: now,
      })
      .run();

    // Increment UPI transaction count if associated
    if (data.upiAccountId) {
      db.update(schema.upiAccounts)
        .set({
          transactionCount: sql`${schema.upiAccounts.transactionCount} + 1`,
          updatedAt: now,
        })
        .where(eq(schema.upiAccounts.id, data.upiAccountId))
        .run();
    }

    // Outbox event for background notification and delta sync worker
    db.insert(schema.outboxEvents)
      .values({
        id: generateId("evt"),
        organizationId: orgId,
        eventType: "transaction.created",
        payloadJson: JSON.stringify({
          id: txnId,
          transactionId: txnId,
          organizationId: orgId,
          bankAccountId: data.bankAccountId ?? null,
          upiAccountId: data.upiAccountId ?? null,
          type: data.type,
          direction: data.direction,
          amount: data.amount,
          currency: "INR",
          status: initialStatus,
          paymentMethod: "UPI",
          referenceNumber: data.referenceNumber ?? null,
          payerName: data.payerName ?? null,
          payerVpa: data.payerVpa ?? null,
          payeeName: data.payeeName,
          payeeVpa: data.payeeVpa,
          note: data.note ?? null,
          occurredAt: now.getTime(),
          createdAt: now.getTime(),
        }),
        status: "PENDING",
        createdAt: now,
      })
      .run();

    return c.json(
      {
        success: true,
        transaction: {
          id: txnId,
          amount: data.amount,
          currency: "INR",
          status: initialStatus,
          payeeVpa: data.payeeVpa,
          occurredAt: now,
        },
      },
      201
    );
  }
);

// Update transaction status (e.g. cash collected, manual verification, refund)
transactionsRouter.patch(
  "/:orgId/transactions/:id/status",
  requireTenant,
  requirePermission("transactions.refund"),
  async (c) => {
    const orgId = c.get("organizationId");
    const txnId = c.req.param("id");
    const body = await c.req.json();

    const validator = z.object({
      status: z.enum(["SUCCESS", "FAILED", "REFUNDED", "EXPIRED"]),
      referenceNumber: z.string().optional(),
      note: z.string().optional(),
    });

    const data = validator.parse(body);
    const now = new Date();

    const txn = db
      .select()
      .from(schema.transactions)
      .where(and(eq(schema.transactions.id, txnId), eq(schema.transactions.organizationId, orgId)))
      .get();

    if (!txn) {
      throw new NotFoundError("Transaction not found");
    }

    const previousStatus = txn.status;

    db.update(schema.transactions)
      .set({
        status: data.status,
        referenceNumber: data.referenceNumber ?? txn.referenceNumber,
        note: data.note ?? txn.note,
        updatedAt: now,
      })
      .where(eq(schema.transactions.id, txnId))
      .run();

    // Record transaction event
    db.insert(schema.transactionEvents)
      .values({
        id: generateId("txnev"),
        transactionId: txnId,
        organizationId: orgId,
        eventType: data.status === "SUCCESS" ? "transaction.success" : (data.status === "REFUNDED" ? "transaction.refunded" : "transaction.failed"),
        previousStatus,
        newStatus: data.status,
        payloadJson: JSON.stringify({ referenceNumber: data.referenceNumber, note: data.note }),
        createdAt: now,
      })
      .run();

    // Outbox event for delta-sync
    db.insert(schema.outboxEvents)
      .values({
        id: generateId("evt"),
        organizationId: orgId,
        eventType: "transaction.status_changed",
        payloadJson: JSON.stringify({
          id: txnId,
          transactionId: txnId,
          organizationId: orgId,
          status: data.status,
          previousStatus,
          referenceNumber: data.referenceNumber ?? txn.referenceNumber,
          updatedAt: now.getTime(),
        }),
        status: "PENDING",
        createdAt: now,
      })
      .run();

    return c.json({
      success: true,
      message: "Transaction status updated",
      transaction: {
        id: txnId,
        status: data.status,
        previousStatus,
        referenceNumber: data.referenceNumber ?? txn.referenceNumber,
      },
    });
  }
);

