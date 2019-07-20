package me.ivanyu.paxos

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InOrder
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class AcceptorTest {

    private val ACCEPTOR_ID: AcceptorId = "acceptor"

    @Mock
    private lateinit var acceptorDisk: AcceptorDisk
    private lateinit var inOrder: InOrder

    @BeforeTest
    fun beforeEach() {
        inOrder = inOrder(acceptorDisk)
    }

    private val PROPOSER_A: ProposerId = "proposer_a";
    private val PROPOSER_B: ProposerId = "proposer_b";

    @Test
    fun `Acceptor should accept any Prepare and return null as accepted value if it hasn't given promise before`() {
        val acceptor = Acceptor(ACCEPTOR_ID, acceptorDisk)
        val proposalId1 = ProposalId(1, PROPOSER_A)
        assertEquals(
                Promise(proposalId1, null, null),
                acceptor.receivePrepare(Prepare(proposalId1))
        )

        inOrder.verify(acceptorDisk, times(1)).write(eq(proposalId1), isNull(), isNull())
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `Acceptor should tolerate duplicate Prepare if it hasn't given promise before`() {
        val acceptor = Acceptor(ACCEPTOR_ID, acceptorDisk)
        val proposalId1 = ProposalId(1, PROPOSER_A)
        assertEquals(
                Promise(proposalId1, null, null),
                acceptor.receivePrepare(Prepare(proposalId1))
        )
        assertEquals(
                Promise(proposalId1, null, null),
                acceptor.receivePrepare(Prepare(proposalId1))
        )

        inOrder.verify(acceptorDisk, times(2)).write(eq(proposalId1), isNull(), isNull())
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `Acceptor shouldn't give Promise to any Prepare with lower proposal ID than last promised`() {
        val acceptor = Acceptor(ACCEPTOR_ID, acceptorDisk)
        val proposalId1 = ProposalId(1, PROPOSER_A)

        acceptor.receivePrepare(Prepare(proposalId1))
        assertNull(
                acceptor.receivePrepare(Prepare(ProposalId(0, PROPOSER_B)))
        )

        inOrder.verify(acceptorDisk, times(1)).write(eq(proposalId1), isNull(), isNull())
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `Acceptor shouldn't accept anything with lower proposal ID than last promised`() {
        val acceptor = Acceptor(ACCEPTOR_ID, acceptorDisk)
        val proposalId1 = ProposalId(1, PROPOSER_A)

        acceptor.receivePrepare(Prepare(proposalId1))
        assertNull(acceptor.receiveAccept(Accept(ProposalId(0, PROPOSER_B), "some_value")))

        inOrder.verify(acceptorDisk, times(1)).write(eq(proposalId1), isNull(), isNull())
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `Acceptor should accept value with equal proposal ID as promised`() {
        val acceptor = Acceptor(ACCEPTOR_ID, acceptorDisk)
        val proposalId = ProposalId(1, PROPOSER_A)
        val value = "some_value"

        acceptor.receivePrepare(Prepare(proposalId))
        assertEquals(
                Accepted(proposalId, value),
                acceptor.receiveAccept(Accept(proposalId, value))
        )

        inOrder.verify(acceptorDisk, times(1)).write(eq(proposalId), isNull(), isNull())
        inOrder.verify(acceptorDisk, times(1)).write(proposalId, proposalId, value)
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `Acceptor should accept value with higher proposal ID than promised`() {
        val acceptor = Acceptor(ACCEPTOR_ID, acceptorDisk)
        val proposalId1 = ProposalId(1, PROPOSER_A)
        val proposalId2 = ProposalId(1, PROPOSER_B)
        val value = "some_value"

        acceptor.receivePrepare(Prepare(proposalId1))
        assertEquals(
                Accepted(proposalId2, value),
                acceptor.receiveAccept(Accept(proposalId2, value))
        )

        inOrder.verify(acceptorDisk, times(1)).write(eq(proposalId1), isNull(), isNull())
        inOrder.verify(acceptorDisk, times(1)).write(proposalId2, proposalId2, value)
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `Acceptor should return accepted value when asked to Prepare if it has accepted one already`() {
        val acceptor = Acceptor(ACCEPTOR_ID, acceptorDisk)
        val proposalId1 = ProposalId(1, PROPOSER_A)
        val proposalId2 = ProposalId(2, PROPOSER_B)
        val value = "some_value"

        acceptor.receivePrepare(Prepare(proposalId1))
        acceptor.receiveAccept(Accept(proposalId1, value))

        assertEquals(
                Promise(proposalId2, proposalId1, value),
                acceptor.receivePrepare(Prepare(proposalId2))
        )

        inOrder.verify(acceptorDisk, times(1)).write(eq(proposalId1), isNull(), isNull())
        inOrder.verify(acceptorDisk, times(1)).write(proposalId1, proposalId1, value)
        inOrder.verify(acceptorDisk, times(1)).write(proposalId2, proposalId1, value)
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `Acceptor should tolerate duplicate Prepare if it has accepted value already`() {
        val acceptor = Acceptor(ACCEPTOR_ID, acceptorDisk)
        val proposalId1 = ProposalId(1, PROPOSER_A)
        val proposalId2 = ProposalId(2, PROPOSER_B)
        val value = "some_value"

        acceptor.receivePrepare(Prepare(proposalId1))
        acceptor.receiveAccept(Accept(proposalId1, value))

        assertEquals(
                Promise(proposalId2, proposalId1, value),
                acceptor.receivePrepare(Prepare(proposalId2))
        )
        assertEquals(
                Promise(proposalId2, proposalId1, value),
                acceptor.receivePrepare(Prepare(proposalId2))
        )

        inOrder.verify(acceptorDisk, times(1)).write(eq(proposalId1), isNull(), isNull())
        inOrder.verify(acceptorDisk, times(1)).write(proposalId1, proposalId1, value)
        inOrder.verify(acceptorDisk, times(2)).write(proposalId2, proposalId1, value)
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `Acceptor can change accepted value`() {
        val acceptor = Acceptor(ACCEPTOR_ID, acceptorDisk)
        val proposalId1 = ProposalId(1, PROPOSER_A)
        val proposalId2 = ProposalId(2, PROPOSER_B)
        val proposalId3 = ProposalId(3, PROPOSER_A)
        val value1 = "some_value"
        val value2 = "another_value"

        acceptor.receivePrepare(Prepare(proposalId1))
        acceptor.receiveAccept(Accept(proposalId1, value1))

        acceptor.receivePrepare(Prepare(proposalId2))
        acceptor.receiveAccept(Accept(proposalId2, value2))

        assertEquals(
                Promise(proposalId3, proposalId2, value2),
                acceptor.receivePrepare(Prepare(proposalId3))
        )

        inOrder.verify(acceptorDisk, times(1)).write(eq(proposalId1), isNull(), isNull())
        inOrder.verify(acceptorDisk, times(1)).write(proposalId1, proposalId1, value1)
        inOrder.verify(acceptorDisk, times(1)).write(proposalId2, proposalId1, value1)
        inOrder.verify(acceptorDisk, times(1)).write(proposalId2, proposalId2, value2)
        inOrder.verify(acceptorDisk, times(1)).write(proposalId3, proposalId2, value2)
        inOrder.verifyNoMoreInteractions()
    }

    @Test
    fun `Acceptor should accept if it hasn't given promise before`() {
        val acceptor = Acceptor(ACCEPTOR_ID, acceptorDisk)
        val proposalId1 = ProposalId(1, PROPOSER_B)
        val value = "some_value"

        assertEquals(
                Accepted(proposalId1, value),
                acceptor.receiveAccept(Accept(proposalId1, value))
        )

        inOrder.verify(acceptorDisk, times(1)).write(proposalId1, proposalId1, value)
        inOrder.verifyNoMoreInteractions()
    }
}
