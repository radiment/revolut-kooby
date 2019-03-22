package com.revolut.test.account.err

import org.jooby.Status
import java.lang.RuntimeException

open class AccountErr(override val message: String, val status: Status = Status.BAD_REQUEST)
    : RuntimeException(message)