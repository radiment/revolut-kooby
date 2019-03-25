package com.revolut.test.account

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.revolut.test.account.dao.AccountRepo
import com.revolut.test.account.dao.TransitionRepo
import com.revolut.test.account.err.AccountErr
import com.revolut.test.account.err.NotFoundErr
import com.revolut.test.account.model.*
import org.jdbi.v3.core.Handle
import org.jooby.*
import org.jooby.Jooby.run
import org.jooby.flyway.Flywaydb
import org.jooby.hbv.Hbv
import org.jooby.jdbc.Jdbc
import org.jooby.jdbi.Jdbi3
import org.jooby.jdbi.TransactionalRequest
import org.jooby.json.Jackson
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.ForkJoinPool
import javax.validation.ConstraintViolationException


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
                    .attach(TransitionRepo::class.java)
                    .attach(AccountRepo::class.java)))
    use(Jackson().module(KotlinModule::class.java))
    executor(ForkJoinPool())

    path("/") {
        get("/accounts") {
            require(AccountRepo::class).getAllAccounts()
        }

        post("/accounts") {
            val account = body(Account::class.java)
            val accountRepo = require(AccountRepo::class)
            val id = accountRepo.insertAccount(account)
            val acc = accountRepo.getAccount(id)
            Results.with(acc, 201)
        }

        get("/transactions/:id") {
            val id: UUID = param("id").to(UUID::class)
            require(TransitionRepo::class).getTransaction(id)
        }

        get("/accounts/:id") {
            val id: Long = param("id").longValue()
            require(AccountRepo::class).getAccount(id)
        }

        get("/accounts/:id/transitions") {
            val id: Long = param("id").longValue()
            require(TransitionRepo::class).getAccountTransitions(id)
        }

        get("/users/:userId/accounts") {
            val userId: UUID = param("userId").to(UUID::class)
            require(AccountRepo::class).getAccountsForUser(userId)
        }

        post("/users/:userId/income") {
            val account = body(Account::class.java)
            val userId: UUID = param("userId").to(UUID::class)
            val accountRepo = require(AccountRepo::class)
            val transitionRepo = require(TransitionRepo::class)

            val exist = accountRepo.getAccount(userId, account.currencyId)
                    .orElseThrow { NotFoundErr("Account not found") }
            val amount = exist.amount + account.amount
            accountRepo.tryUpdate(exist.id, amount, exist.amount)

            transitionRepo.transition(exist.id, account.amount, TransactionType.INCOME)
            require(Handle::class).commit()
            accountRepo.getAccount(exist.id)
        }

        post("/users/:userId/withdraw") {
            val account = body(Account::class.java)
            val userId: UUID = param("userId").to(UUID::class)
            val accountRepo = require(AccountRepo::class)
            val transitionRepo = require(TransitionRepo::class)

            val exist = accountRepo.getAccount(userId, account.currencyId)
                    .orElseThrow { NotFoundErr("Account not found") }
            checkFunds(exist.amount, account.amount, "withdrawal")
            val amount = exist.amount - account.amount
            accountRepo.tryUpdate(exist.id, amount, exist.amount)
            transitionRepo.transition(exist.id, -account.amount, TransactionType.WITHDRAWAL)
            accountRepo.getAccount(exist.id)
        }

        post("/transfers") {
            val transfer = body(Transfer::class.java)
            val accountRepo = require(AccountRepo::class)
            val transitionRepo = require(TransitionRepo::class)
            val amount = transfer.amount

            val from = accountRepo.getAccount(transfer.userFrom, transfer.currencyId)
                    .orElseThrow { NotFoundErr("Account from not found") }
            checkFunds(from.amount, amount, "transfer")

            val to = accountRepo.getAccount(transfer.userTo, transfer.currencyId)
                    .orElseThrow { NotFoundErr("Account to not found") }

            runInOrder(from.id > to.id, Runnable {
                accountRepo.tryUpdate(from.id, from.amount - amount, from.amount)
            }, Runnable {
                accountRepo.tryUpdate(to.id, to.amount + amount, to.amount)
            })

            val transaction = UUID.randomUUID()
            transitionRepo
                    .transition(from.id, -amount, TransactionType.TRANSFER, transaction)
                    .transition(to.id, amount, TransactionType.TRANSFER, transaction)

            Transaction(transaction, amount)
        }

        err(AccountErr::class.java) { _, rsp, err ->
            val accountErr = err.cause as AccountErr
            val error = Error(accountErr.message, accountErr.status.value())
            rsp.send(Results.with(error, accountErr.status))
        }

        err(ConstraintViolationException::class.java) { _, rsp, err ->
            val cause = err.cause as ConstraintViolationException
            val constraints = cause.constraintViolations

            val errors = constraints.associateBy({ it.propertyPath.toString() }, { it.message })
            rsp.send(Error("Validation error", 400, errors))
        }

    }.consumes("json").produces("json")

})

private fun runInOrder(natureOrder: Boolean, first: Runnable, second: Runnable) {
  if (natureOrder) {
      first.run()
      second.run()
  } else {
      second.run()
      first.run()
  }
}

private fun AccountRepo.tryUpdate(id: Long, amount: BigDecimal, oldAmount: BigDecimal)
        : AccountRepo {
    if (!this.updateAmount(id, amount, oldAmount)) {
        throw AccountErr("Concurrent update of the amount", Status.CONFLICT)
    }
    return this
}


private fun TransitionRepo.transition(account: Long,
                                      amount: BigDecimal, type: TransactionType): TransitionRepo {
    return this.transition(account, amount, type, UUID.randomUUID())
}

private fun TransitionRepo.transition(account: Long,
                                      amount: BigDecimal, type: TransactionType,
                                      transactionId: UUID): TransitionRepo {
    this.createTransition(Transition(null, account, transactionId, type, amount))
    return this
}

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
