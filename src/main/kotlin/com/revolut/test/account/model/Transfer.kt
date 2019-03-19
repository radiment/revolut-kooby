package com.revolut.test.account.model

import java.math.BigDecimal
import java.util.*
import javax.validation.constraints.DecimalMin
import javax.validation.constraints.NotNull

data class Transfer(@field:NotNull val userFrom: UUID,
                    @field:NotNull val userTo: UUID,
                    @field:NotNull val currencyId: Int,
                    @field:DecimalMin("0", inclusive = false) val amount: BigDecimal)