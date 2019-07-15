package me.ivanyu.paxos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AcceptorTest {
    private val PROPOSER_A: ProposerId = "proposer_a";
    private val PROPOSER_B: ProposerId = "proposer_b";

    @Test fun `Acceptor should accept any Prepare and return null as accepted value if it hasn't given promise before`() {
        val acceptor = Acceptor()
        assertEquals(
                Promise(ProposalId(1, PROPOSER_A), null, null),
                acceptor.receivePrepare(Prepare(ProposalId(1, PROPOSER_A)))
        )
    }

    @Test fun `Acceptor should tolerate duplicate Prepare if it hasn't given promise before`() {
        val acceptor = Acceptor()
        assertEquals(
                Promise(ProposalId(1, PROPOSER_A), null, null),
                acceptor.receivePrepare(Prepare(ProposalId(1, PROPOSER_A)))
        )
        assertEquals(
                Promise(ProposalId(1, PROPOSER_A), null, null),
                acceptor.receivePrepare(Prepare(ProposalId(1, PROPOSER_A)))
        )
    }

    @Test fun `Acceptor shouldn't give Promise to any Prepare with lower proposal ID than last promised`() {
        val acceptor = Acceptor()
        acceptor.receivePrepare(Prepare(ProposalId(1, PROPOSER_A)))
        assertNull(
                acceptor.receivePrepare(Prepare(ProposalId(0, PROPOSER_B)))
        )
    }

    @Test fun `Acceptor shouldn't accept anything with lower proposal ID than last promised`() {
        val acceptor = Acceptor()
        acceptor.receivePrepare(Prepare(ProposalId(1, PROPOSER_A)))
        assertNull(acceptor.receiveAccept(Accept(ProposalId(0, PROPOSER_B), "some_value")))
    }

    @Test fun `Acceptor should accept value with equal proposal ID as promised`() {
        val acceptor = Acceptor()
        acceptor.receivePrepare(Prepare(ProposalId(1, PROPOSER_A)))

        assertEquals(
                Accepted(ProposalId(1, PROPOSER_A), "some_value"),
                acceptor.receiveAccept(Accept(ProposalId(1, PROPOSER_A), "some_value"))
        )
    }

    @Test fun `Acceptor should accept value with higher proposal ID than promised`() {
        val acceptor = Acceptor()
        acceptor.receivePrepare(Prepare(ProposalId(1, PROPOSER_A)))

        assertEquals(
                Accepted(ProposalId(1, PROPOSER_B), "another_value"),
                acceptor.receiveAccept(Accept(ProposalId(1, PROPOSER_B), "another_value"))
        )
    }

    @Test fun `Acceptor should return accepted value when asked to Prepare if it has accepted one already`() {
        val acceptor = Acceptor()
        acceptor.receivePrepare(Prepare(ProposalId(1, PROPOSER_A)))
        acceptor.receiveAccept(Accept(ProposalId(1, PROPOSER_A), "some_value"))

        assertEquals(
                Promise(ProposalId(2, PROPOSER_B), ProposalId(1, PROPOSER_A), "some_value"),
                acceptor.receivePrepare(Prepare(ProposalId(2, PROPOSER_B)))
        )
    }

    @Test fun `Acceptor should tolerate duplicate Prepare if it has accepted value already`() {
        val acceptor = Acceptor()
        acceptor.receivePrepare(Prepare(ProposalId(1, PROPOSER_A)))
        acceptor.receiveAccept(Accept(ProposalId(1, PROPOSER_A), "some_value"))

        assertEquals(
                Promise(ProposalId(2, PROPOSER_B), ProposalId(1, PROPOSER_A), "some_value"),
                acceptor.receivePrepare(Prepare(ProposalId(2, PROPOSER_B)))
        )
        assertEquals(
                Promise(ProposalId(2, PROPOSER_B), ProposalId(1, PROPOSER_A), "some_value"),
                acceptor.receivePrepare(Prepare(ProposalId(2, PROPOSER_B)))
        )
    }

    @Test fun `Acceptor can change accepted value`() {
        val acceptor = Acceptor()
        acceptor.receivePrepare(Prepare(ProposalId(1, PROPOSER_A)))
        acceptor.receiveAccept(Accept(ProposalId(1, PROPOSER_A), "some_value"))

        acceptor.receivePrepare(Prepare(ProposalId(2, PROPOSER_B)))
        acceptor.receiveAccept(Accept(ProposalId(2, PROPOSER_B), "another_value"))

        assertEquals(
                Promise(ProposalId(3, PROPOSER_A), ProposalId(2, PROPOSER_B), "another_value"),
                acceptor.receivePrepare(Prepare(ProposalId(3, PROPOSER_A)))
        )
    }

    @Test fun `Acceptor should accept if it hasn't given promise before`() {
        val acceptor = Acceptor()
        assertEquals(
                Accepted(ProposalId(1, PROPOSER_B), "another_value"),
                acceptor.receiveAccept(Accept(ProposalId(1, PROPOSER_B), "another_value"))
        )
    }
}
