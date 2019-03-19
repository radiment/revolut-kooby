package com.revolut.test.account.model

data class ValidationError(val message: String,
                           val messages: Map<String, String>,
                           val status: Int)