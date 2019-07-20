package me.ivanyu.paxos

import java.lang.AssertionError
import kotlin.test.*

class ProposerTest {
    private val ACCEPTOR_A: ProposerId = "acceptor_a"
    private val ACCEPTOR_B: ProposerId = "acceptor_b"
    private val PROPOSER_A: ProposerId = "proposer_a"
    private val PROPOSER_B: ProposerId = "proposer_b"
    private val PROPOSER_C: ProposerId = "proposer_c"
    private val VALUE_1: Value = "value_1"
    private val VALUE_2: Value = "value_2"
    private val VALUE_3: Value = "value_3"

    @Test
    fun `Proposer should not allow calling other methods before round is started`() {
        val proposer = Proposer(PROPOSER_A, VALUE_1, -1)
        assertFailsWith<IllegalStateException> {
            proposer.receivePromise(ACCEPTOR_A, Promise(ProposalId(0, PROPOSER_A), null, null))
        }
    }

    @Test
    fun `Proposer should increase the round number`() {
        val proposer = Proposer(PROPOSER_A, VALUE_1, -1)
        val proposalId1 = proposer.nextRound().proposalId
        assertEquals(PROPOSER_A, proposalId1.proposerId)

        val proposalId2 = proposer.nextRound().proposalId
        assertEquals(PROPOSER_A, proposalId2.proposerId)
        assertTrue(proposalId2 > proposalId1)
    }

    @Test
    fun `Proposer should not return Accept if quorum hasn't been reached`() {
        val proposer = Proposer(PROPOSER_A, VALUE_1, 2)
        proposer.nextRound()
        assertNull(proposer.receivePromise(ACCEPTOR_A, Promise(ProposalId(0, PROPOSER_A), null, null)))
    }

    @Test
    fun `Proposer should return Accept for its value when no other values have been accepted`() {
        val proposer = Proposer(PROPOSER_A, VALUE_1, 2)
        val initialProposalId = proposer.nextRound().proposalId
        assertNull(proposer.receivePromise(ACCEPTOR_A, Promise(ProposalId(0, PROPOSER_A), null, null)))
        assertEquals(
                Accept(initialProposalId, VALUE_1),
                proposer.receivePromise(ACCEPTOR_B, Promise(ProposalId(0, PROPOSER_A), null, null))
        )
    }

    @Test
    fun `Proposer should return Accept for latest accepted value it knows about`() {
        val proposer = Proposer(PROPOSER_A, VALUE_1, 2)
        proposer.nextRound()
        proposer.nextRound()
        val proposalId = proposer.nextRound().proposalId
        assertEquals(2, proposalId.round)

        val highestProposalId = ProposalId(1, PROPOSER_C)
        assertNull(proposer.receivePromise(ACCEPTOR_A, Promise(proposalId, ProposalId(1, PROPOSER_B), VALUE_2)))
        assertEquals(
                Accept(proposalId, VALUE_3),
                proposer.receivePromise(ACCEPTOR_B, Promise(proposalId, highestProposalId, VALUE_3))
        )
    }

    @Test
    fun `Proposer should tolerate multiple Promise from one acceptor`() {
        val proposer = Proposer(PROPOSER_A, VALUE_1, 2)
        proposer.nextRound()
        for (i in 1..3) {
            assertNull(proposer.receivePromise(ACCEPTOR_A, Promise(ProposalId(0, PROPOSER_A), null, null)))
        }
    }

    @Test
    fun `Proposer should ignore old Promises`() {
        val proposer = Proposer(PROPOSER_A, VALUE_1, 2)
        proposer.nextRound()
        proposer.nextRound()
        for (i in 1..3) {
            assertNull(proposer.receivePromise(ACCEPTOR_A, Promise(ProposalId(0, PROPOSER_A), null, null)))
        }
    }

    @Test
    fun `Proposer should fail on incorrect proposer ID in Promise`() {
        val proposer = Proposer(PROPOSER_A, VALUE_1, 2)
        proposer.nextRound()
        assertFailsWith<AssertionError> {
            proposer.receivePromise(ACCEPTOR_A, Promise(ProposalId(0, PROPOSER_B), null, null))
        }
    }

    @Test
    fun `Proposer receive Accept and reply that value is not committed if quorum is not reached`() {
        val proposer = Proposer(PROPOSER_A, VALUE_1, 2)
        proposer.nextRound()
        assertFalse(
                proposer.receiveAccepted(ACCEPTOR_A, Accepted(ProposalId(0, PROPOSER_A), "some_value"))
        )
    }

    @Test
    fun `Proposer receive Accept and reply that value is committed if quorum is reached`() {
        val proposer = Proposer(PROPOSER_A, VALUE_1, 2)
        proposer.nextRound()
        proposer.receiveAccepted(ACCEPTOR_A, Accepted(ProposalId(0, PROPOSER_A), "some_value"))
        assertTrue(
                proposer.receiveAccepted(ACCEPTOR_B, Accepted(ProposalId(0, PROPOSER_A), "some_value"))
        )
    }

    @Test
    fun `Proposer should tolerate multiple Accepted from one acceptor`() {
        val proposer = Proposer(PROPOSER_A, VALUE_1, 2)
        proposer.nextRound()
        assertFalse(
                proposer.receiveAccepted(ACCEPTOR_A, Accepted(ProposalId(0, PROPOSER_A), "some_value"))
        )
        assertFalse(
                proposer.receiveAccepted(ACCEPTOR_A, Accepted(ProposalId(0, PROPOSER_A), "some_value"))
        )
        assertFalse(
                proposer.receiveAccepted(ACCEPTOR_A, Accepted(ProposalId(0, PROPOSER_A), "some_value"))
        )
    }

    @Test
    fun `Proposer should fail on incorrect proposer ID in Accept`() {
        val proposer = Proposer(PROPOSER_A, VALUE_1, 2)
        proposer.nextRound()
        assertFailsWith<AssertionError> {
            proposer.receiveAccepted(ACCEPTOR_A, Accepted(ProposalId(0, PROPOSER_B), "some_value"))
        }
    }
}
