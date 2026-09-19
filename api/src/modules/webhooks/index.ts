import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and, or } from "drizzle-orm";
import { generateId, verifyHmac } from "../../lib/crypto.js";
import { config } from "../../config/index.js";
import { UnauthorizedError, AppError } from "../../lib/errors.js";
import { logger } from "../../lib/logger.js";
import { notifyOrganizationPayment } from "../notifications/service.js";

export const webhooksRouter = new Hono();

webhooksRouter.post("/:provider", async (c) => {
  const provider = c.req.param("provider").toUpperCase();
  const signature = c.req.header("x-webhook-signature") || c.req.header("x-signature");

  const rawBody = await c.req.text();

  // Signature verification (using configured or provider secret)
  const isValid = signature ? verifyHmac(config.WEBHOOK_SECRET, rawBody, signature) : false;

  // In production mode, reject invalid signatures strictly
  if (config.NODE_ENV === "production" && !isValid) {
    throw new UnauthorizedError("Invalid webhook signature");
  }

  let payload: any;
  try {
    payload = JSON.parse(rawBody);
  } catch {
    throw new AppError("Malformed JSON in webhook body", 400);
  }

  const webhookId = generateId("wh");
  const now = new Date();

  // Log raw webhook
  db.insert(schema.providerWebhooks)
    .values({
      id: webhookId,
      providerName: provider,
      eventId: payload.eventId || payload.id || null,
      signature: signature ?? null,
      payloadJson: rawBody,
      status: "RECEIVED",
      createdAt: now,
    })
    .run();

  // Validate standard payload expectations
  // { referenceNumber, providerTransactionId, amount, status: 'SUCCESS'|'FAILED', upiId, organizationId }
  const refNum = payload.referenceNumber || payload.rrn;
  const providerTxnId = payload.providerTransactionId || payload.bankTransactionId;
  const status = payload.status === "SUCCESS" ? "SUCCESS" : "FAILED";
  const amount = Number(payload.amount);

  if (!refNum && !providerTxnId) {
    return c.json({ received: true, note: "Unindexed webhook recorded" });
  }

  // Find matching pending transaction
  const matchConditions = [];
  if (refNum) matchConditions.push(eq(schema.transactions.referenceNumber, refNum));
  if (providerTxnId) matchConditions.push(eq(schema.transactions.providerTransactionId, providerTxnId));

  const txn = db
    .select()
    .from(schema.transactions)
    .where(or(...matchConditions))
    .get();

  if (txn) {
    // Check if already reconciled to prevent duplicate status changes
    if (txn.status === status) {
      db.update(schema.providerWebhooks)
        .set({ status: "IGNORED", errorMessage: "Duplicate webhook event" })
        .where(eq(schema.providerWebhooks.id, webhookId))
        .run();
      return c.json({ received: true, message: "Duplicate event acknowledged" });
    }

    // Update transaction to authoritative state
    db.update(schema.transactions)
      .set({
        status,
        provider,
        providerTransactionId: providerTxnId ?? txn.providerTransactionId,
        referenceNumber: refNum ?? txn.referenceNumber,
        updatedAt: now,
      })
      .where(eq(schema.transactions.id, txn.id))
      .run();

    // Record transaction event
    db.insert(schema.transactionEvents)
      .values({
        id: generateId("txnev"),
        transactionId: txn.id,
        organizationId: txn.organizationId,
        eventType: status === "SUCCESS" ? "transaction.success" : "transaction.failed",
        previousStatus: txn.status,
        newStatus: status,
        payloadJson: rawBody,
        createdAt: now,
      })
      .run();

    // Record reconciliation record
    db.insert(schema.reconciliationRecords)
      .values({
        id: generateId("rec"),
        organizationId: txn.organizationId,
        transactionId: txn.id,
        providerName: provider,
        providerTransactionId: providerTxnId || refNum,
        referenceNumber: refNum ?? null,
        matchStatus: "MATCHED",
        reconciledAt: now,
        createdAt: now,
      })
      .run();

    // Create Outbox event for mobile notification and delta sync
    db.insert(schema.outboxEvents)
      .values({
        id: generateId("evt"),
        organizationId: txn.organizationId,
        eventType: status === "SUCCESS" ? "transaction.reconciled" : "transaction.failed",
        payloadJson: JSON.stringify({
          id: txn.id,
          transactionId: txn.id,
          organizationId: txn.organizationId,
          amount: txn.amount,
          direction: txn.direction || "CREDIT",
          status: status === "SUCCESS" ? "CONFIRMED" : "FAILED",
          currency: "INR",
          paymentMethod: "UPI",
          referenceNumber: refNum ?? txn.referenceNumber,
          payerName: txn.payerName ?? "UPI Customer",
          payerVpa: txn.payerVpa ?? null,
          payeeName: txn.payeeName,
          payeeVpa: txn.payeeVpa,
          occurredAt: now.getTime(),
        }),
        status: "PENDING",
        createdAt: now,
      })
      .run();

    if (status === "SUCCESS") {
      notifyOrganizationPayment(txn.organizationId, {
        transactionId: txn.id,
        amount: txn.amount,
        direction: txn.direction || "CREDIT",
        referenceNumber: refNum ?? txn.referenceNumber,
        payerName: txn.payerName ?? "UPI Customer",
        payerVpa: txn.payerVpa ?? null,
        payeeName: txn.payeeName,
        payeeVpa: txn.payeeVpa,
        occurredAt: now.getTime(),
      });
    }

    // Mark webhook as processed
    db.update(schema.providerWebhooks)
      .set({ status: "PROCESSED" })
      .where(eq(schema.providerWebhooks.id, webhookId))
      .run();

    logger.info({ txnId: txn.id, status }, "Transaction reconciled from authorized webhook");
  } else {
    // Unmatched provider transaction: create a reconciliation discrepancy record
    if (payload.organizationId) {
      db.insert(schema.reconciliationRecords)
        .values({
          id: generateId("rec"),
          organizationId: payload.organizationId,
          transactionId: null,
          providerName: provider,
          providerTransactionId: providerTxnId || refNum,
          referenceNumber: refNum ?? null,
          matchStatus: "UNMATCHED",
          discrepancyReason: "No corresponding pending transaction found",
          reconciledAt: now,
          createdAt: now,
        })
        .run();
    }
  }

  return c.json({ received: true, success: true });
});
