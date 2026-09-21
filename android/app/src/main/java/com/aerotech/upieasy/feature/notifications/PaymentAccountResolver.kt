package com.aerotech.upieasy.feature.notifications

import com.aerotech.upieasy.core.database.entity.PaymentAccountEntity

data class AccountResolutionResult(
    val status: PaymentMatchStatus,
    val paymentAccountId: String?,
    val candidateAccounts: List<PaymentAccountEntity>
)

class PaymentAccountResolver {

    /**
     * Resolves matching payment account for a parsed event from a given source package.
     */
    fun resolve(
        sourcePackage: String,
        payerVpa: String?,
        availableAccounts: List<PaymentAccountEntity>
    ): AccountResolutionResult {
        // Filter to active accounts for this package with detection enabled
        val matchingPackageAccounts = availableAccounts.filter {
            it.paymentAppPackage == sourcePackage && it.detectionEnabled && it.status == "ACTIVE"
        }

        return when {
            matchingPackageAccounts.isEmpty() -> {
                AccountResolutionResult(
                    status = PaymentMatchStatus.UNMATCHED,
                    paymentAccountId = null,
                    candidateAccounts = emptyList()
                )
            }
            matchingPackageAccounts.size == 1 -> {
                AccountResolutionResult(
                    status = PaymentMatchStatus.MATCHED,
                    paymentAccountId = matchingPackageAccounts.first().id,
                    candidateAccounts = matchingPackageAccounts
                )
            }
            else -> {
                // Multiple accounts exist for this package.
                // If notification contains receiving VPA (or distinctive VPA), check if one matches
                val vpaMatched = if (payerVpa != null) {
                    matchingPackageAccounts.find { it.upiId.equals(payerVpa, ignoreCase = true) }
                } else null

                if (vpaMatched != null) {
                    AccountResolutionResult(
                        status = PaymentMatchStatus.MATCHED,
                        paymentAccountId = vpaMatched.id,
                        candidateAccounts = listOf(vpaMatched)
                    )
                } else {
                    // Multi-account ambiguity per Section 18 of specification:
                    // Never guess between multiple accounts!
                    AccountResolutionResult(
                        status = PaymentMatchStatus.AMBIGUOUS,
                        paymentAccountId = null,
                        candidateAccounts = matchingPackageAccounts
                    )
                }
            }
        }
    }
}
