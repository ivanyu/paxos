package me.ivanyu.paxos.simulation

import me.ivanyu.paxos.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@ExtendWith(MockitoExtension::class)
class ProposerActorTest {
    private val ROUND_TRIP_MS = 10L
    private val ACCEPTOR_A: ProposerId = "acceptor_a"
    private val ACCEPTOR_B: ProposerId = "acceptor_b"
    private val PROPOSER_A: ProposerId = "proposer_a"
    private val PROPOSER_B: ProposerId = "proposer_b"
    private val PROPOSER_C: ProposerId = "proposer_c"
    private val VALUE_1: Value = "value_1"
    private val VALUE_2: Value = "value_2"
    private val VALUE_3: Value = "value_3"

    private val QUORUM_SIZE = 2

//    private val time: Time = SystemTime()
    @Mock
    private lateinit var time: Time
    @Mock
    private lateinit var network: ProposerNetwork
    private lateinit var actor: ProposerActor

    @BeforeTest
    fun beforeEach() {
        val proposer = Proposer(PROPOSER_B, VALUE_2, QUORUM_SIZE, false)
        actor = ProposerActor(ROUND_TRIP_MS, time, proposer)
        actor.attachNetwork(network)
    }

    @AfterTest
    fun afterEach() {
        // In case a tests fails, interrupt anyway.
        actor.interrupt()
    }

    @Test
    fun `ProposerActor should work fine in happy path`() {
        val proposalId1 = ProposalId(0, PROPOSER_B)
        val proposalId2 = ProposalId(1, PROPOSER_B)
        `when`(network.poll(sameNonNull(actor), anyLong()))
                .thenReturn(Pair(ACCEPTOR_A, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_B, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_A, Accepted(proposalId1, VALUE_2)))
                .thenReturn(Pair(ACCEPTOR_B, Accepted(proposalId1, VALUE_2)))
        actor.start()
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId1))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Accept(proposalId1, VALUE_2))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId2))
        actor.interrupt()
    }

    @Test
    fun `ProposerActor should discover an existing value and switch to it`() {
        val proposalId0 = ProposalId(0, PROPOSER_A)
        val proposalId1 = ProposalId(0, PROPOSER_B)

        `when`(network.poll(sameNonNull(actor), anyLong()))
                .thenReturn(Pair(ACCEPTOR_A, Promise(proposalId1, proposalId0, VALUE_1)))
                .thenReturn(Pair(ACCEPTOR_B, Promise(proposalId1, null, null)))
        actor.start()
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId1))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Accept(proposalId1, VALUE_1))
        actor.interrupt()
    }

    @Test
    fun `ProposerActor should restart from Phase 1 on timeout in Phase 1`() {
        val proposalId1 = ProposalId(0, PROPOSER_B)
        val proposalId2 = ProposalId(1, PROPOSER_B)
        `when`(time.now())
                .thenReturn(0)
                .thenReturn(ROUND_TRIP_MS * 100)
                .thenReturn(ROUND_TRIP_MS * 200)
                .thenReturn(Long.MAX_VALUE)
        actor.start()
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId1))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId2))
        actor.interrupt()
    }

    @Test
    fun `ProposerActor should restart from Phase 1 on timeout in Phase 2`() {
        val proposalId1 = ProposalId(0, PROPOSER_B)
        val proposalId2 = ProposalId(1, PROPOSER_B)
        `when`(time.now())
                // Phase 1, 1 time.
                .thenReturn(0) // start of timer
                .thenReturn(0, 0) // 1 iteration of polling
                .thenReturn(0, 0) // 2 iteration of polling
                // Phase 2, 1 time.
                .thenReturn(ROUND_TRIP_MS * 100) // start of timer
                .thenReturn(ROUND_TRIP_MS * 100, ROUND_TRIP_MS * 100) // 1 iteration of polling
                .thenReturn(ROUND_TRIP_MS * 1000) // 1 iteration of polling
                // Phase 1, 2 time.
                .thenReturn(0) // start of timer
                .thenReturn(0, 0) // 1 iteration of polling
                .thenReturn(0, 0) // 2 iteration of polling
                .thenReturn(Long.MAX_VALUE)
        `when`(network.poll(sameNonNull(actor), anyLong()))
                .thenReturn(Pair(ACCEPTOR_A, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_B, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_A, Accepted(proposalId1, VALUE_2)))
                .thenReturn(Pair(ACCEPTOR_A, Promise(proposalId2, proposalId1, VALUE_2)))
                .thenReturn(Pair(ACCEPTOR_B, Promise(proposalId2, proposalId1, VALUE_2)))
        actor.start()
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId1))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Accept(proposalId1, VALUE_2))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId2))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Accept(proposalId2, VALUE_2))
        actor.interrupt()
    }

    @Test
    fun `Proposer shouldn't be disrupted by duplicate messages in Phase 1`() {
        val proposalId1 = ProposalId(0, PROPOSER_B)
        val proposalId2 = ProposalId(1, PROPOSER_B)
        `when`(network.poll(sameNonNull(actor), anyLong()))
                .thenReturn(Pair(ACCEPTOR_A, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_A, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_B, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_B, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_A, Accepted(proposalId1, VALUE_2)))
                .thenReturn(Pair(ACCEPTOR_B, Accepted(proposalId1, VALUE_2)))
        actor.start()
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId1))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Accept(proposalId1, VALUE_2))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId2))
        actor.interrupt()
    }

    @Test
    fun `Proposer shouldn't be disrupted by irrelevant messages in Phase 1`() {
        val proposalId1 = ProposalId(0, PROPOSER_B)
        val proposalId2 = ProposalId(1, PROPOSER_B)
        `when`(network.poll(sameNonNull(actor), anyLong()))
                .thenReturn(Pair(ACCEPTOR_A, Promise(ProposalId(-1, PROPOSER_B), null, null)))
                .thenReturn(Pair(ACCEPTOR_A, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_B, Promise(ProposalId(100, PROPOSER_B), null, null)))
                .thenReturn(Pair(ACCEPTOR_B, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_A, Accepted(proposalId1, VALUE_2)))
                .thenReturn(Pair(ACCEPTOR_B, Accepted(proposalId1, VALUE_2)))
        actor.start()
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId1))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Accept(proposalId1, VALUE_2))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId2))
        actor.interrupt()
    }

    @Test
    fun `Proposer shouldn't be disrupted by duplicate messages in Phase 2`() {
        val proposalId1 = ProposalId(0, PROPOSER_B)
        val proposalId2 = ProposalId(1, PROPOSER_B)
        `when`(network.poll(sameNonNull(actor), anyLong()))
                .thenReturn(Pair(ACCEPTOR_A, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_B, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_A, Accepted(proposalId1, VALUE_2)))
                .thenReturn(Pair(ACCEPTOR_A, Accepted(proposalId1, VALUE_2)))
                .thenReturn(Pair(ACCEPTOR_B, Accepted(proposalId1, VALUE_2)))
                .thenReturn(Pair(ACCEPTOR_B, Accepted(proposalId1, VALUE_2)))
        actor.start()
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId1))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Accept(proposalId1, VALUE_2))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId2))
        actor.interrupt()
    }

    @Test
    fun `Proposer shouldn't be disrupted by irrelevant messages in Phase 2`() {
        val proposalId1 = ProposalId(0, PROPOSER_B)
        val proposalId2 = ProposalId(1, PROPOSER_B)
        `when`(network.poll(sameNonNull(actor), anyLong()))
                .thenReturn(Pair(ACCEPTOR_A, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_B, Promise(proposalId1, null, null)))
                .thenReturn(Pair(ACCEPTOR_A, Accepted(ProposalId(-1, PROPOSER_B), VALUE_2)))
                .thenReturn(Pair(ACCEPTOR_A, Accepted(proposalId1, VALUE_2)))
                .thenReturn(Pair(ACCEPTOR_B, Accepted(ProposalId(100, PROPOSER_B), VALUE_2)))
                .thenReturn(Pair(ACCEPTOR_B, Accepted(proposalId1, VALUE_2)))
        actor.start()
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId1))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Accept(proposalId1, VALUE_2))
        verify(network, timeout(1000).times(1)).broadcastToAcceptors(PROPOSER_B, Prepare(proposalId2))
        actor.interrupt()
    }
}
