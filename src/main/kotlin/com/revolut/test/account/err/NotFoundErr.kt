package com.revolut.test.account.err

import org.jooby.Status

open class NotFoundErr(message: String) : AccountErr(message, Status.NOT_FOUND) {
}