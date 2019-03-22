package com.revolut.test.account.model

data class Error(val message: String,
                 val status: Int,
                 val messages: Map<String, String>? = null)