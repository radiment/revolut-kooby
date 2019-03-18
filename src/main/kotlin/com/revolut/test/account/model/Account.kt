package com.revolut.test.account.model

import java.math.BigDecimal
import java.util.*

data class Account(val id: Long? = null,
                   val userId: UUID? = null,
                   val currencyId: Int,
                   val amount: BigDecimal = BigDecimal.ZERO)