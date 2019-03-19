package com.revolut.test.account.model

import java.math.BigDecimal
import java.util.*
import javax.validation.constraints.DecimalMin
import javax.validation.constraints.NotNull

data class Account(val id: Long = -1,
                   val userId: UUID? = null,
                   @field:NotNull val currencyId: Int,
                   @field:DecimalMin("0") val amount: BigDecimal = BigDecimal.ZERO)