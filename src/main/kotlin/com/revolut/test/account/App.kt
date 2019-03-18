package com.revolut.test.account

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.davidmoten.rx.RxUtil
import com.github.davidmoten.rx.jdbc.Database
import com.revolut.test.account.err.AccountErr
import com.revolut.test.account.model.Account
import com.revolut.test.account.model.Transfer
import org.jooby.Jooby.run
import org.jooby.Kooby
import org.jooby.Results
import org.jooby.flyway.Flywaydb
import org.jooby.jdbc.Jdbc
import org.jooby.json.Jackson
import org.jooby.require
import org.jooby.rx.Rx
import org.jooby.rx.RxJdbc
import org.jooby.to
import rx.Observable
import java.util.*


/**
 * Gradle Kotlin stater project.
 */
class App : Kooby({

    use(Rx())
    use(Jdbc())
    use(Flywaydb())
    use(RxJdbc())
    use(Jackson().module(KotlinModule::class.java))

    path("/") {
        get {
            require(Database::class)
                    .select("select * from account")
                    .autoMap(Account::class.java)
                    .toList()
        }

        post {
            val account = body(Account::class.java)
            val db = require(Database::class)
            val inserted = insertAccount(db, account)
            selectAccount(db)
                    .parameters(inserted)
                    .autoMap(Account::class.java)
                    .single()
                    .map { acc -> Results.with(acc, 201) }
        }

        get("/:id") {
            val id: Long = param("id").longValue()
            val db = require(Database::class)
            selectAccount(db)
                    .parameter(id)
                    .autoMap(Account::class.java)
                    .single()
        }

        get("/users/:userId") {
            val userId: UUID = param("userId").to(UUID::class)
            require(Database::class)
                    .select("select * from account where user_id = ?")
                    .parameter(userId)
                    .autoMap(Account::class.java)
                    .toList()
        }

        post("/users/:userId/income") {
            val account = body(Account::class.java)
            val userId: UUID = param("userId").to(UUID::class)
            val db = require(Database::class)
            val begin = db.beginTransaction()
            val altAccount = selectAccountByUserAndCurrency(db)
                    .dependsOn(begin)
                    .parameters(userId, account.currencyId)
                    .autoMap(Account::class.java)
                    .single()
            val amountPair = altAccount
                    .flatMap { acc -> Observable.just(acc.amount + account.amount, acc.id) }
            val updateAccount = updateAccount(db)
                    .parameters(amountPair)
                    .count()
            val end = db.commit(updateAccount)
            selectAccount(db)
                    .dependsOn(end)
                    .parameters(altAccount.map { acc -> acc.id })
                    .autoMap(Account::class.java)
                    .single()
        }

        post("/users/:userId/withdraw") {
            val account = body(Account::class.java)
            val userId: UUID = param("userId").to(UUID::class)
            val db = require(Database::class)
            db.beginTransaction()
                    .compose { selectAccount(db, userId, account.currencyId) }
                    .single()
                    .filter { acc -> acc.amount >= account.amount }
                    .switchIfEmpty(Observable.error(AccountErr(
                            "User account doesn't have enough funds for withdrawal")))
                    .flatMap { acc -> Observable.just(acc.amount - account.amount, acc.id) }
                    .compose(updateAccount(db).parameterTransformer())
                    .map(RxUtil.toEmpty())
                    .compose(db.commit_())
        }

        post("/transfers") {
            val transfer = body(Transfer::class.java)
            val db = require(Database::class)
            db.beginTransaction()
                    .compose {
                        selectAccount(db, transfer.userFrom, transfer.currencyId)
                    }
                    .single()
                    .filter { acc -> acc.amount >= transfer.amount }
                    .switchIfEmpty(Observable.error(AccountErr(
                            "User account doesn't have enough funds for transfer")))
                    .flatMap { acc -> Observable.just(acc.amount - transfer.amount, acc.id) }
                    .compose(updateAccount(db).parameterTransformer())
                    .map(RxUtil.toEmpty())
                    .compose {
                        selectAccountByUserAndCurrency(db)
                                .dependsOn(it)
                                .parameters(transfer.userTo, transfer.currencyId)
                                .autoMap(Account::class.java)
                    }
                    .single()
                    .switchIfEmpty(Observable.error(AccountErr(
                            "User account doesn't exist")))
                    .flatMap { acc -> Observable.just(acc.amount + transfer.amount, acc.id) }
                    .compose(updateAccount(db).parameterTransformer())
                    .map(RxUtil.toEmpty())
                    .compose(db.commit_())
        }

    }.consumes("json").produces("json")

})

private fun selectAccount(db: Database,
                          userId: UUID,
                          currencyId: Int) = selectAccountByUserAndCurrency(db)
        .parameters(userId, currencyId)
        .autoMap(Account::class.java)


private fun selectAccountByUserAndCurrency(db: Database) = db
        .select("select * from account where user_id = ? and currency_id = ?")

private fun selectAccount(db: Database) =
        db.select("select * from account where id = ?")

private fun insertAccount(db: Database, alterAcc: Account) =
        db.update("insert into account(user_id, currency_id, amount) values(?,?,?)")
                .parameters(alterAcc.userId, alterAcc.currencyId, alterAcc.amount)
                .returnGeneratedKeys()
                .getAs(Long::class.java)
                .single()
                .switchIfEmpty(Observable.error(AccountErr(
                        "User account has not been found")))

private fun updateAccount(db: Database) =
        db.update("update account set amount = ? where id = ?")


/**
 * Run application:
 */
fun main(args: Array<String>) {
    run(::App, args)
}
