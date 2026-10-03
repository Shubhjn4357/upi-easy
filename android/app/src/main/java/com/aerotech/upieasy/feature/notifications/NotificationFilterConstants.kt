package com.aerotech.upieasy.feature.notifications

/**
 * Universal list of non-payment, marketing, promotional, bill reminder, and non-settlement keywords
 * shared across all UPI notification parsers (Google Pay, PhonePe, BHIM, Paytm).
 *
 * If any incoming notification contains one of these keywords, it is disqualified from being
 * treated as an inbound/outbound financial money transfer.
 */
object NotificationFilterConstants {

    val COMMON_NON_PAYMENT_KEYWORDS: List<String> = listOf(
        // Offers, Cashback, Deals & Gamification
        "offer", "offers", "deal", "deals", "discount", "discounts", "sale", "special",
        "cashback", "scratch card", "scratchcard", "reward", "rewards", "coupon", "coupons",
        "voucher", "vouchers", "voucher code", "promo", "promotional", "exclusive",
        "win up to", "win upto", "won up to", "won upto", "get up to", "get upto",
        "flat rs", "flat ₹", "flat inr", "save up to", "save upto", "save rs", "save ₹",
        "bumper", "jackpot", "festive", "explore", "claim your", "claim now",
        "refer", "referral", "invite friends", "invite your", "spin & win", "spin and win",
        "spin to win", "spin the wheel", "contest", "play now", "earn up to", "earn upto",
        "congratulations",

        // Financial Products, Upselling & Wealth
        "loan", "pre-approved", "preapproved", "personal loan", "business loan",
        "insurance", "policy", "premium", "mutual fund", "mutual funds", "sip", "gold",
        "digital gold", "credit card", "credit score", "cibil score", "cibil", "fixed deposit",

        // Bills, Recharges & Reminders (Not completed payments)
        "recharge offer", "bill offer", "recharge now", "bill due", "bill generated",
        "due on", "due date", "upcoming bill", "bill payment due", "payment due",
        "electricity bill", "water bill", "gas bill", "broadband", "fastag", "dth",
        "payment request", "requested money", "requested rs", "requested ₹",
        "has requested", "requested you", "remind", "reminder", "pay request",
        "autopay scheduled", "autopay due", "mandate created", "mandate approved",
        "e-mandate", "standing instruction",

        // Commerce, Delivery & Food Aggregators
        "order placed", "order confirmed", "order delivered", "swiggy", "zomato",
        "flipkart", "amazon", "myntra", "blinkit", "zepto", "instamart", "uber", "ola",

        // System, Security, Feedback & Failed/Reversed Transactions
        "kyc", "update kyc", "kyc pending", "rate us", "feedback", "survey",
        "security alert", "login alert", "otp", "verification code", "update available",
        "new feature", "don't miss", "hurry up", "limited time", "limited period",
        "failed", "declined", "rejected", "reversed", "refunded", "cancelled", "canceled"
    )
}
