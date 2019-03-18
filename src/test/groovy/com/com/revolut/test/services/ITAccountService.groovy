package com.com.revolut.test.services

import groovyx.net.http.RESTClient
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import com.revolut.test.account.App

import static groovyx.net.http.ContentType.JSON
import static org.hamcrest.Matchers.*
import static spock.util.matcher.HamcrestSupport.expect

@Stepwise
class ITAccountService extends Specification {

    public static final int PORT = 16666
    private static final String USER1 = "d55b82b0-70b5-468a-afff-9dd943bf8b8c"
    private static final String USER2 = "6a34743d-8106-4f54-bf93-c7dffdb23402"
    private static final int CURRENCY = 810


    @Shared def client
    @Shared def accountId
    @Shared @AutoCleanup("stop")
    App app

    def setupSpec() {
        app = new App()
        app.start("server.join=false", "application.port=$PORT")
        client = new RESTClient("http://localhost:$PORT", JSON)
        client.handler.failure = client.handler.success
    }

    def "create account" () {
        given: "user ids and currency and amount for the first"
        def amount = 50
        def account = [currencyId: CURRENCY, userId: USER1, amount: amount]

        when: "request account for users"
        def resp = client.post(path: "/", body: account)

        then:
        resp.status == 201
        resp.data.userId == USER1
        resp.data.amount == amount
        resp.data.currencyId == CURRENCY
        (accountId = resp.data.id) != null
    }

    def "create income to user 1" () {
        given: "100 rub for income"
        def income = [amount:100, currencyId: CURRENCY]

        when: "request income for user1"
        def resp = client.post(path: "/users/${USER1}/income", body: income)

        then:
        resp.status == 200
        resp.data.userId == USER1
        resp.data.amount == 150
        resp.data.currencyId == CURRENCY

    }

    def "get all accounts"() {
        when:
        def resp = client.get(path: '/')

        then:
        resp.status == 200
        expect(resp.data.id, contains(accountId))
        expect(resp.data.userId, contains(USER1))
    }

    def "get accounts for non existent user returns empty list"() {
        when:
        def resp = client.get(path: "/users/$USER2")

        then:
        resp.status == 200
        expect(resp.data, empty())
    }

    def "create second account" () {
        given: "user ids and currency"
        def account2 = [currencyId: CURRENCY, userId: USER2]

        when: "request account for users"
        def resp = client.post(path: "/", body: account2)

        then:
        resp.status == 201
        resp.data.userId == USER2
        resp.data.amount == 0
        resp.data.currencyId == CURRENCY
    }

    def "get accounts for existent user returns account data"() {
        when:
        def resp = client.get(path: "/users/$USER1")

        then:
        resp.status == 200
        expect(resp.data, not(empty()))
        expect(resp.data.currencyId, contains(CURRENCY))
        expect(resp.data.userId, contains(USER1))
    }

    def "income increase existed value account"() {
        given: "350 rub for income"
        def income = [amount: 350, currencyId: CURRENCY]

        when: "request income for user1"
        def resp = client.post(path: "/users/${USER1}/income", body: income)

        then:
        resp.status == 200
        resp.data.amount == 500
        resp.data.currencyId == CURRENCY
        resp.data.id == accountId
    }

    def "transfer money from non existent user will fail"() {
        given:
        def transfer = [userFrom: USER2, userTo: USER1, amount: 100, currencyId: CURRENCY]

        when:
        def resp = client.post(path: '/transfers', body: transfer)
        def from = client.get(path: "/users/$USER2")
        def to = client.get(path: "/users/$USER1")

        then:
        resp.status == 400
        resp.data.status == 400
        resp.data.message == "User account doesn't have enough funds for transfer"
        expect(to.data.amount, contains(500))
        expect(from.data.amount, contains(0))
    }

    def "transfer money from non existent user will fail2"() {
        given:
        def transfer = [userFrom: USER2, userTo: USER1, amount: 100, currencyId: CURRENCY]

        when:
        def resp = client.post(path: '/transfers', body: transfer)
        def from = client.get(path: "/users/$USER2")
        def to = client.get(path: "/users/$USER1")

        then:
        resp.status == 400
        resp.data.status == 400
        resp.data.message == "User account doesn't have enough funds for transfer"
        expect(to.data.amount, contains(500))
        expect(from.data.amount, contains(0))
    }

    def "transfer money if it's enough to any person is ok"() {
        given:
        def transfer = [userFrom: USER1, userTo: USER2, amount: 100, currencyId: CURRENCY]

        when:
        def resp = client.post(path: '/transfers', body: transfer)
        def from = client.get(path: "/users/$USER1")
        def to = client.get(path: "/users/$USER2")

        then:
        resp.status == 200
        resp.data.message == 'Transfer successful'
        expect(from.data.amount, contains(400))
        expect(to.data.amount, contains(100))
    }

    def "transfer money when it's not enough failed"() {
        given:
        def transfer = [userFrom: USER1, userTo: USER2, value: 400, currencyId: CURRENCY]

        when:
        def resp = client.post(path: '/transfers', body: transfer)
        def from = client.get(path: "/users/$USER1")
        def to = client.get(path: "/users/$USER2")

        then:
        resp.status == 400
//        resp.data.code == NOT_ENOUGH_MONEY.getCode()
        expect(from.data.amount, contains(400))
        expect(to.data.amount, contains(100))
    }
}