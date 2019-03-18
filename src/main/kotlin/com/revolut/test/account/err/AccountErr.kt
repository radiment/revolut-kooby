package com.revolut.test.account.err

import java.lang.RuntimeException

open class AccountErr(message: String?) : RuntimeException(message)