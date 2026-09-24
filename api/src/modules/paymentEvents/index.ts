import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and, desc, sql } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { requireAuth } from "../../middleware/auth.js";
import { requireTenant } from "../../middleware/tenant.js";
import { requirePermission } from "../../middleware/rbac.js";
import { notifyOrganizationPayment } from "../notifications/service.js";
import { NotFoundError } from "../../lib/errors.js";
import type { AppEnv } from "../../types/hono.js";

export const paymentEventsRouter = new Hono<AppEnv>();

paymentEventsRouter.use("*", requireAuth);

const observedPaymentEventSchema = z.object({
  clientEventId: z.string().min(1).max(128),
  source: z.object({
    type: z.enum(["NOTIFICATION_PHONEPE", "NOTIFICATION_GPAY"]),
    packageName: z.string().min(1),
  }),
  paymentAccountId: z.string().nullable().optional(),
  qrId: z.string().nullable().optional(),
  amountMinor: z.number().int().positive().nullable().optional(),
  currency: z.literal("INR").default("INR"),
  direction: z.enum(["RECEIVED", "SENT", "UNKNOWN"]).default("RECEIVED"),
  payerName: z.string().max(200).nullable().optional(),
  payerVpa: z.string().max(255).nullable().optional(),
  reference: z.string().max(255).nullable().optional(),
  observedAt: z.string(), // ISO string or timestamp string
  verificationStatus: z.literal("OBSERVED").default("OBSERVED"),
  matchStatus: z.enum(["MATCHED", "UNMATCHED", "AMBIGUOUS"]).default("MATCHED"),
  fingerprint: z.string().min(16).max(128),
});

// Ingest observed payment event
paymentEventsRouter.post(
  "/:orgId/payment-events/observed",
  requireTenant,
  async (c, next) => {
    // Check permission: payment_events.ingest or transactions.create
    const userRole = c.get("role");
    const perms = c.get("permissions") || [];
    const hasPerm =
      perms.includes("payment_events.ingest") ||
      perms.includes("transactions.create") ||
      userRole === "OWNER" ||
      userRole === "MANAGER" ||
      userRole === "CASHIER";

    if (!hasPerm) {
      return c.json(
        { success: false, error: "Missing required permission to ingest payment events" },
        403
      );
    }
    await next();
  },
  async (c) => {
    const orgId = c.get("organizationId");
    const actorId = c.get("userId");
    const body = await c.req.json();

    const data = observedPaymentEventSchema.parse(body);

    // Section 49: Check Deduplication / Idempotency by (organizationId, eventFingerprint)
    const existingEvent = await db
      .select()
      .from(schema.observedPaymentEvents)
      .where(
        and(
          eq(schema.observedPaymentEvents.organizationId, orgId),
          eq(schema.observedPaymentEvents.eventFingerprint, data.fingerprint)
        )
      )
      .get();

    if (existingEvent) {
      // Find the associated transaction if one was created
      const existingTxn = await db
        .select()
        .from(schema.transactions)
        .where(
          and(
            eq(schema.transactions.organizationId, orgId),
            eq(schema.transactions.referenceNumber, data.reference || "")
          )
        )
        .get();

      return c.json({
        accepted: true,
        eventId: existingEvent.id,
        transactionId: existingTxn?.id || null,
        status: "OBSERVED",
        duplicate: true,
      });
    }

    const eventId = generateId("evt_obs");
    const txnId = generateId("txn");
    const now = new Date();
    const observedDate = new Date(data.observedAt);
    const validObservedDate = isNaN(observedDate.getTime()) ? now : observedDate;

    // Minor amount in paise to double rupees
    const amountRupees = data.amountMinor ? data.amountMinor / 100.0 : 0.0;

    // Validate payment account ownership if provided
    let verifiedPaymentAccountId: string | null = null;
    let paymentAccountLabel = "Payment Account";
    let payeeVpa = "merchant@upi";

    if (data.paymentAccountId) {
      const pa = await db
        .select()
        .from(schema.paymentAccounts)
        .where(
          and(
            eq(schema.paymentAccounts.id, data.paymentAccountId),
            eq(schema.paymentAccounts.organizationId, orgId)
          )
        )
        .get();

      if (pa) {
        verifiedPaymentAccountId = pa.id;
        paymentAccountLabel = pa.label;
        payeeVpa = pa.upiId;

        // Update lastNotificationDetectedAt on payment account
        await db
          .update(schema.paymentAccounts)
          .set({
            lastNotificationDetectedAt: now,
            updatedAt: now,
          })
          .where(eq(schema.paymentAccounts.id, pa.id))
          .run();
      }
    }

    // 1. Insert observed payment event record
    await db
      .insert(schema.observedPaymentEvents)
      .values({
        id: eventId,
        organizationId: orgId,
        paymentAccountId: verifiedPaymentAccountId,
        qrId: data.qrId || null,
        sourceType: data.source.type,
        sourcePackage: data.source.packageName,
        amountMinor: data.amountMinor ?? null,
        currency: data.currency,
        direction: data.direction,
        payerName: data.payerName || null,
        payerVpa: data.payerVpa || null,
        reference: data.reference || null,
        eventFingerprint: data.fingerprint,
        matchStatus: data.matchStatus,
        verificationStatus: "OBSERVED",
        observedAt: validObservedDate,
        createdAt: now,
      })
      .run();

    // 2. Insert transaction record with status UNKNOWN and verificationStatus OBSERVED
    // Note: Per Section 2 & 25, observed payments must NEVER be marked as SUCCESS directly
    await db
      .insert(schema.transactions)
      .values({
        id: txnId,
        organizationId: orgId,
        bankAccountId: null,
        upiAccountId: null,
        paymentAccountId: verifiedPaymentAccountId,
        type: "PAYMENT",
        direction: data.direction === "SENT" ? "SENT" : "RECEIVED",
        amount: amountRupees,
        currency: "INR",
        status: "UNKNOWN",
        verificationStatus: "OBSERVED",
        eventSource: data.source.type,
        paymentMethod: "UPI",
        provider: "NPCI",
        referenceNumber: data.reference || null,
        payerName: data.payerName || null,
        payerVpa: data.payerVpa || null,
        payeeName: paymentAccountLabel,
        payeeVpa: payeeVpa,
        note: `Observed from ${data.source.type.replace("NOTIFICATION_", "")} notification`,
        staffId: actorId,
        source: data.source.type,
        occurredAt: validObservedDate,
        createdAt: now,
        updatedAt: now,
      })
      .run();

    // 3. Insert transaction event
    await db
      .insert(schema.transactionEvents)
      .values({
        id: generateId("txnev"),
        transactionId: txnId,
        organizationId: orgId,
        eventType: "transaction.observed",
        previousStatus: null,
        newStatus: "UNKNOWN",
        payloadJson: JSON.stringify({
          source: data.source.type,
          amountMinor: data.amountMinor,
          reference: data.reference,
          fingerprint: data.fingerprint,
        }),
        createdAt: now,
      })
      .run();

    // 4. Insert Outbox Event: PAYMENT_OBSERVED
    await db
      .insert(schema.outboxEvents)
      .values({
        id: generateId("evt"),
        organizationId: orgId,
        eventType: "PAYMENT_OBSERVED",
        payloadJson: JSON.stringify({
          type: "PAYMENT_OBSERVED",
          eventId,
          transactionId: txnId,
          organizationId: orgId,
          paymentAccountId: verifiedPaymentAccountId,
          amountMinor: data.amountMinor ?? 0,
          currency: "INR",
          source: data.source.type.replace("NOTIFICATION_", ""),
          reference: data.reference,
          payerName: data.payerName,
          observedAt: validObservedDate.toISOString(),
        }),
        status: "PENDING",
        createdAt: now,
      })
      .run();

    // 4b. Insert Outbox Event: transaction.created for mobile & ledger delta sync
    await db
      .insert(schema.outboxEvents)
      .values({
        id: generateId("evt"),
        organizationId: orgId,
        eventType: "transaction.created",
        payloadJson: JSON.stringify({
          id: txnId,
          transactionId: txnId,
          organizationId: orgId,
          paymentAccountId: verifiedPaymentAccountId,
          type: "PAYMENT",
          direction: data.direction === "SENT" ? "SENT" : "RECEIVED",
          amount: amountRupees,
          currency: "INR",
          status: "SUCCESS",
          paymentMethod: "UPI",
          referenceNumber: data.reference || null,
          payerName: data.payerName || null,
          payerVpa: data.payerVpa || null,
          payeeName: paymentAccountLabel,
          payeeVpa: payeeVpa,
          note: `Observed from ${data.source.type.replace("NOTIFICATION_", "")} notification`,
          occurredAt: validObservedDate.getTime(),
          createdAt: now.getTime(),
        }),
        status: "PENDING",
        createdAt: now,
      })
      .run();

    // 5. Trigger notification distribution for staff devices
    try {
      await notifyOrganizationPayment(orgId, {
        transactionId: txnId,
        amount: amountRupees,
        direction: data.direction === "SENT" ? "SENT" : "RECEIVED",
        referenceNumber: data.reference || null,
        payerName: data.payerName || null,
        payeeVpa: payeeVpa,
      });
    } catch (_) {
      // Non-blocking notification delivery failure
    }

    return c.json(
      {
        accepted: true,
        eventId,
        transactionId: txnId,
        status: "OBSERVED",
      },
      201
    );
  }
);

// List observed payment events
paymentEventsRouter.get(
  "/:orgId/payment-events",
  requireTenant,
  requirePermission("transactions.read"),
  async (c) => {
    const orgId = c.get("organizationId");
    const query = c.req.query();
    const limit = Math.min(Number(query.limit) || 20, 100);
    const offset = Number(query.offset) || 0;

    const events = await db
      .select()
      .from(schema.observedPaymentEvents)
      .where(eq(schema.observedPaymentEvents.organizationId, orgId))
      .orderBy(desc(schema.observedPaymentEvents.observedAt))
      .limit(limit)
      .offset(offset)
      .all();

    const totalCount =
      (
        await db
          .select({ count: sql<number>`count(*)` })
          .from(schema.observedPaymentEvents)
          .where(eq(schema.observedPaymentEvents.organizationId, orgId))
          .get()
      )?.count ?? 0;

    return c.json({
      success: true,
      data: events,
      pagination: {
        total: totalCount,
        limit,
        offset,
        hasMore: offset + events.length < totalCount,
      },
    });
  }
);

// Get single observed payment event
paymentEventsRouter.get(
  "/:orgId/payment-events/:id",
  requireTenant,
  requirePermission("transactions.read"),
  async (c) => {
    const orgId = c.get("organizationId");
    const id = c.req.param("id");

    const event = await db
      .select()
      .from(schema.observedPaymentEvents)
      .where(
        and(
          eq(schema.observedPaymentEvents.id, id),
          eq(schema.observedPaymentEvents.organizationId, orgId)
        )
      )
      .get();

    if (!event) {
      throw new NotFoundError("Observed payment event not found");
    }

    return c.json({ success: true, data: event });
  }
);
