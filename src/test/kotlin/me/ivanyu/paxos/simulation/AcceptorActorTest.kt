package me.ivanyu.paxos.simulation

import me.ivanyu.paxos.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.internal.stubbing.answers.AnswersWithDelay
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@ExtendWith(MockitoExtension::class)
class AcceptorActorTest {
    private val ACCEPTOR_ID: AcceptorId = "acceptor"

    private val PROPOSER_A: ProposerId = "proposer_a";
    private val PROPOSER_B: ProposerId = "proposer_b";

    @Mock
    private lateinit var disk: AcceptorDisk
    @Mock
    private lateinit var channel: AcceptorChannel
    private lateinit var actor: AcceptorActor

    @BeforeTest
    fun beforeEach() {
        val acceptor = Acceptor(ACCEPTOR_ID, disk)
        actor = AcceptorActor(acceptor)
        actor.attachChannel(channel)
    }

    @AfterTest
    fun afterEach() {
        // In case a tests fails, interrupt anyway.
        actor.interrupt()
    }

    @Test
    fun `AcceptorActor should accept any Prepare and return null as accepted value if it hasn't given promise before`() {
        val proposalId1 = ProposalId(1, PROPOSER_A)
        `when`(channel.poll())
                .thenReturn(Pair(PROPOSER_A, Prepare(proposalId1)))
                .thenAnswer(AnswersWithDelay(Long.MAX_VALUE, null))
        actor.start()
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Promise(proposalId1, null, null))
        actor.interrupt()
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `AcceptorActor should tolerate duplicate Prepare if it hasn't given promise before`() {
        val proposalId1 = ProposalId(1, PROPOSER_A)
        `when`(channel.poll())
                .thenReturn(Pair(PROPOSER_A, Prepare(proposalId1)))
                .thenReturn(Pair(PROPOSER_A, Prepare(proposalId1)))
                .thenAnswer(AnswersWithDelay(Long.MAX_VALUE, null))
        actor.start()
        verify(channel, timeout(1000).times(2)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Promise(proposalId1, null, null))
        actor.interrupt()
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `AcceptorActor shouldn't give Promise to any Prepare with lower proposal ID than last promised`() {
        val proposalId1 = ProposalId(1, PROPOSER_A)
        val proposalId0 = ProposalId(0, PROPOSER_B)
        `when`(channel.poll())
                .thenReturn(Pair(PROPOSER_A, Prepare(proposalId1)))
                .thenReturn(Pair(PROPOSER_A, Prepare(proposalId0)))
                .thenAnswer(AnswersWithDelay(Long.MAX_VALUE, null))
        actor.start()
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Promise(proposalId1, null, null))
        actor.interrupt()
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `AcceptorActor shouldn't accept anything with lower proposal ID than last promised`() {
        val proposalId1 = ProposalId(1, PROPOSER_A)
        val proposalId0 = ProposalId(0, PROPOSER_B)
        `when`(channel.poll())
                .thenReturn(Pair(PROPOSER_A, Prepare(proposalId1)))
                .thenReturn(Pair(PROPOSER_A, Accept(proposalId0, "some_value")))
                .thenAnswer(AnswersWithDelay(Long.MAX_VALUE, null))
        actor.start()
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Promise(proposalId1, null, null))
        Thread.sleep(1000)
        actor.interrupt()
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `AcceptorActor should accept value with equal proposal ID as promised`() {
        val proposalId = ProposalId(1, PROPOSER_A)
        val value = "some_value"
        `when`(channel.poll())
                .thenReturn(Pair(PROPOSER_A, Prepare(proposalId)))
                .thenReturn(Pair(PROPOSER_A, Accept(proposalId, value)))
                .thenAnswer(AnswersWithDelay(Long.MAX_VALUE, null))
        actor.start()
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Promise(proposalId, null, null))
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Accepted(proposalId, value))
        actor.interrupt()
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `AcceptorActor should accept value with higher proposal ID than promised`() {
        val proposalId1 = ProposalId(1, PROPOSER_A)
        val proposalId2 = ProposalId(1, PROPOSER_B)
        val value = "some_value"
        `when`(channel.poll())
                .thenReturn(Pair(PROPOSER_A, Prepare(proposalId1)))
                .thenReturn(Pair(PROPOSER_A, Accept(proposalId2, value)))
                .thenAnswer(AnswersWithDelay(Long.MAX_VALUE, null))
        actor.start()
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Promise(proposalId1, null, null))
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Accepted(proposalId2, value))
        actor.interrupt()
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `AcceptorActor should return accepted value when asked to Prepare if it has accepted one already`() {
        val proposalId1 = ProposalId(1, PROPOSER_A)
        val proposalId2 = ProposalId(2, PROPOSER_B)
        val value = "some_value"
        `when`(channel.poll())
                .thenReturn(Pair(PROPOSER_A, Prepare(proposalId1)))
                .thenReturn(Pair(PROPOSER_A, Accept(proposalId1, value)))
                .thenReturn(Pair(PROPOSER_B, Prepare(proposalId2)))
                .thenAnswer(AnswersWithDelay(Long.MAX_VALUE, null))
        actor.start()
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Promise(proposalId1, null, null))
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Accepted(proposalId1, value))
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_B, Promise(proposalId2, proposalId1, value))
        actor.interrupt()
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `AcceptorActor should tolerate duplicate Prepare if it has accepted value already`() {
        val proposalId1 = ProposalId(1, PROPOSER_A)
        val proposalId2 = ProposalId(2, PROPOSER_B)
        val value = "some_value"
        `when`(channel.poll())
                .thenReturn(Pair(PROPOSER_A, Prepare(proposalId1)))
                .thenReturn(Pair(PROPOSER_A, Accept(proposalId1, value)))
                .thenReturn(Pair(PROPOSER_B, Prepare(proposalId2)))
                .thenReturn(Pair(PROPOSER_B, Prepare(proposalId2)))
                .thenAnswer(AnswersWithDelay(Long.MAX_VALUE, null))
        actor.start()
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Promise(proposalId1, null, null))
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Accepted(proposalId1, value))
        verify(channel, timeout(1000).times(2)).sendToProposer(ACCEPTOR_ID, PROPOSER_B, Promise(proposalId2, proposalId1, value))
        actor.interrupt()
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `Acceptor can change accepted value`() {
        val proposalId1 = ProposalId(1, PROPOSER_A)
        val proposalId2 = ProposalId(2, PROPOSER_B)
        val proposalId3 = ProposalId(3, PROPOSER_A)
        val value1 = "some_value"
        val value2 = "another_value"
        `when`(channel.poll())
                .thenReturn(Pair(PROPOSER_A, Prepare(proposalId1)))
                .thenReturn(Pair(PROPOSER_A, Accept(proposalId1, value1)))
                .thenReturn(Pair(PROPOSER_B, Prepare(proposalId2)))
                .thenReturn(Pair(PROPOSER_B, Accept(proposalId2, value2)))
                .thenReturn(Pair(PROPOSER_A, Prepare(proposalId3)))
                .thenAnswer(AnswersWithDelay(Long.MAX_VALUE, null))
        actor.start()
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Promise(proposalId1, null, null))
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Accepted(proposalId1, value1))
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_B, Promise(proposalId2, proposalId1, value1))
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_B, Accepted(proposalId2, value2))
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_A, Promise(proposalId3, proposalId2, value2))
        actor.interrupt()
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `AcceptorActor should accept if it hasn't given promise before`() {
        val proposalId1 = ProposalId(1, PROPOSER_B)
        val value = "some_value"
        `when`(channel.poll())
                .thenReturn(Pair(PROPOSER_B, Accept(proposalId1, value)))
                .thenAnswer(AnswersWithDelay(Long.MAX_VALUE, null))
        actor.start()
        verify(channel, timeout(1000).times(1)).sendToProposer(ACCEPTOR_ID, PROPOSER_B, Accepted(proposalId1, value))
        actor.interrupt()
        verifyNoMoreInteractions(channel)
    }
}
