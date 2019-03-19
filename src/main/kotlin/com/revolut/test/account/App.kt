package com.revolut.test.account

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.revolut.test.account.dao.AccountRepo
import com.revolut.test.account.err.AccountErr
import com.revolut.test.account.model.Account
import com.revolut.test.account.model.Transfer
import com.revolut.test.account.model.ValidationError
import org.jdbi.v3.core.Handle
import org.jooby.Jooby.run
import org.jooby.Kooby
import org.jooby.Results
import org.jooby.flyway.Flywaydb
import org.jooby.hbv.Hbv
import org.jooby.jdbc.Jdbc
import org.jooby.jdbi.Jdbi3
import org.jooby.jdbi.TransactionalRequest
import org.jooby.json.Jackson
import org.jooby.require
import org.jooby.to
import java.math.BigDecimal
import java.util.*
import javax.validation.ConstraintViolation
import java.util.stream.Collectors
import javax.validation.ConstraintViolationException
import javax.validation.Path


/**
 * Gradle Kotlin stater project.
 */
class App : Kooby({

    use(Hbv(Account::class.java))
    use(Jdbc())
    use(Flywaydb())
    use(Jdbi3()
            .doWith { jdbi -> jdbi.installPlugins() }
            .transactionPerRequest(TransactionalRequest()
                    .attach(AccountRepo::class.java)))
    use(Jackson().module(KotlinModule::class.java))

    path("/") {
        get {
            require(AccountRepo::class).getAllAccounts()
        }

        post {
            val account = body(Account::class.java)
            val accountRepo = require(AccountRepo::class)
            val id = accountRepo.insertAccount(account)
            val acc = accountRepo.getAccount(id)
            Results.with(acc, 201)
        }

        get("/:id") {
            val id: Long = param("id").longValue()
            require(AccountRepo::class).getAccount(id)
        }

        get("/users/:userId") {
            val userId: UUID = param("userId").to(UUID::class)
            require(AccountRepo::class).getAccountsForUser(userId)
        }

        post("/users/:userId/income") {
            val account = body(Account::class.java)
            val userId: UUID = param("userId").to(UUID::class)
            val accountRepo = require(AccountRepo::class)

            val exist = accountRepo.getAccountByUserAndCurr(userId, account.currencyId)
                    .orElseThrow { AccountErr("Account not found") }
            val amount = exist.amount + account.amount
            val updated = accountRepo.updateAmount(exist.id, amount)
            if (!updated) {
                throw AccountErr("Account has not been updated")
            }
            require(Handle::class).commit()
            accountRepo.getAccount(exist.id)
        }

        post("/users/:userId/withdraw") {
            val account = body(Account::class.java)
            val userId: UUID = param("userId").to(UUID::class)
            val accountRepo = require(AccountRepo::class)

            val exist = accountRepo.getAccountByUserAndCurr(userId, account.currencyId)
                    .orElseThrow { AccountErr("Account not found") }
            checkFunds(exist.amount, account.amount, "withdrawal")
            val amount = exist.amount - account.amount
            accountRepo.updateAmount(exist.id, amount)
            accountRepo.getAccount(exist.id)
        }

        post("/transfers") {
            val transfer = body(Transfer::class.java)
            val accountRepo = require(AccountRepo::class)

            val from = accountRepo.getAccountByUserAndCurr(transfer.userFrom, transfer.currencyId)
                    .orElseThrow { AccountErr("Account from not found") }
            checkFunds(from.amount, transfer.amount, "transfer")

            val to = accountRepo.getAccountByUserAndCurr(transfer.userTo, transfer.currencyId)
                    .orElseThrow { AccountErr("Account to not found") }

            val isOk = accountRepo.updateAmount(from.id, from.amount - transfer.amount)
                    && accountRepo.updateAmount(to.id, to.amount + transfer.amount)
            if (!isOk) {
                throw AccountErr("Some update error")
            }
            Results.noContent()
        }

        err { req, rsp, err ->
            val cause = err.cause
            if (cause is ConstraintViolationException) {
                val constraints = cause.constraintViolations

                val errors = constraints.associateBy({ it.propertyPath.toString() }, { it.message })
                rsp.send(ValidationError("Validation error", errors, 400))
            }
        }

    }.consumes("json").produces("json")

})

private fun checkFunds(amount: BigDecimal, withdrawal: BigDecimal, action: String) {
    if (amount < withdrawal) {
        throw AccountErr("User account doesn't have enough funds for $action")
    }
}

/**
 * Run application:
 */
fun main(args: Array<String>) {
    run(::App, args)
}
