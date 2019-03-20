package com.revolut.test.account.dao

import com.revolut.test.account.model.Transition
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.util.*

interface TransitionRepo {

    @SqlQuery("select * from transition")
    fun getTransitions() : List<Transition>

    @SqlUpdate("insert into transition(account_id, transaction_id, type, amount) " +
            "values (:tr.accountId, :tr.transactionId, :tr.type, :tr.amount)")
    fun createTransition(tr: Transition)

    @SqlQuery("select * from transition where transaction_id = :transactionId")
    fun getTransaction(transactionId: UUID) : List<Transition>

    @SqlQuery("select * from transition where account_id = :accountId")
    fun getAccountTransitions(accountId:Long) : List<Transition>
}