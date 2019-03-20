package com.revolut.test.account.model

import java.math.BigDecimal
import java.util.*

data class Transaction(val transactionId: UUID,
                       val amount: BigDecimal)