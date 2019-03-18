package com.revolut.test.account.model

import java.math.BigDecimal
import java.util.*

data class Transfer(val userFrom: UUID,
                    val userTo: UUID,
                    val currencyId: Int,
                    val amount: BigDecimal)