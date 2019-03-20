package com.revolut.test.account.model

import java.math.BigDecimal
import java.util.*

data class Transition(
        val id: Long?,
        val accountId: Long,
        val transactionId: UUID,
        val type: TransactionType,
        val amount: BigDecimal
)