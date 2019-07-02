package me.ivanyu.paxos

import java.lang.AssertionError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AcceptorTest {

    private val PROPOSER_A = "proposer_a";
    private val PROPOSER_B = "proposer_b";

    @Test fun `Acceptor should accept any prepare and return null as accepted value if it hasn't given a promise before`() {
        val acceptor = Acceptor()
        assertEquals(
                Promise(Ballot(1, PROPOSER_A), null, null),
                acceptor.prepare(Ballot(1, PROPOSER_A))
        )
    }

    @Test fun `Acceptor should not give a promise to any prepare with a lower ballot than the last promised`() {
        val acceptor = Acceptor()
        acceptor.prepare(Ballot(1, PROPOSER_A))
        assertNull(acceptor.prepare(Ballot(0, PROPOSER_B)))
    }

    @Test fun `Acceptor should not accept anything with a lower ballot than the last promised`() {
        val acceptor = Acceptor()
        acceptor.prepare(Ballot(1, PROPOSER_A))

        assertEquals(
                Ballot(1, PROPOSER_A),
                acceptor.accept(Ballot(0, PROPOSER_B), "some_value")
        )
    }

    @Test fun `Acceptor should accept a value with an equal ballot as the promised`() {
        val acceptor = Acceptor()
        acceptor.prepare(Ballot(1, PROPOSER_A))

        assertEquals(
                Ballot(1, PROPOSER_A),
                acceptor.accept(Ballot(1, PROPOSER_A), "some_value")
        )
    }

    @Test fun `Acceptor should accept a value with a higher ballot than the promised`() {
        val acceptor = Acceptor()
        acceptor.prepare(Ballot(1, PROPOSER_A))

        assertEquals(
                Ballot(1, PROPOSER_B),
                acceptor.accept(Ballot(1, PROPOSER_B), "some_value")
        )
    }

    @Test fun `Acceptor should return an accepted value when asked to prepare if it has accepted one already`() {
        val acceptor = Acceptor()
        acceptor.prepare(Ballot(1, PROPOSER_A))
        acceptor.accept(Ballot(1, PROPOSER_A), "some_value")

        assertEquals(
                Promise(Ballot(2, PROPOSER_B), Ballot(1, PROPOSER_A), "some_value"),
                acceptor.prepare(Ballot(2, PROPOSER_B))
        )
    }

    @Test fun `Acceptor can change the accepted value`() {
        val acceptor = Acceptor()
        acceptor.prepare(Ballot(1, PROPOSER_A))
        acceptor.accept(Ballot(1, PROPOSER_A), "some_value")

        acceptor.prepare(Ballot(2, PROPOSER_B))
        acceptor.accept(Ballot(2, PROPOSER_B), "another_value")

        assertEquals(
                Promise(Ballot(3, PROPOSER_A), Ballot(2, PROPOSER_B), "another_value"),
                acceptor.prepare(Ballot(3, PROPOSER_A))
        )
    }
}
