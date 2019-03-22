package com.revolut.test.account.dao

import com.revolut.test.account.model.Account
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.math.BigDecimal
import java.util.*

interface AccountRepo {

    @SqlQuery("select * from account")
    fun getAllAccounts() : List<Account>

    @SqlUpdate("insert into account(user_id, currency_id, amount) " +
            "values(:acc.userId, :acc.currencyId, :acc.amount)")
    @GetGeneratedKeys("id")
    fun insertAccount(acc: Account) : Long

    @SqlQuery("select * from account a where a.id = :id")
    fun getAccount(id: Long) : Account

    @SqlQuery("select * from account a where a.user_id = :userId")
    fun getAccountsForUser(userId: UUID) : List<Account>

    @SqlQuery("select * from account a " +
            "where a.user_id = :userId and currency_id = :currencyId")
    fun getAccountByUserAndCurr(userId: UUID, currencyId: Int) : Optional<Account>

    @SqlUpdate("update account set amount = :amount where id = :id and amount = :oldAmount")
    fun updateAmount(id: Long, amount: BigDecimal, oldAmount: BigDecimal) : Boolean
}