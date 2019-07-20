package me.ivanyu.paxos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProposalIdTest {
    @Test
    fun `ProposalId should be equal to itself`() {
        val b = ProposalId(1, "x")
        assertEquals(b, b)
    }

    @Test
    fun `ProposalId should be greater if round is greater and ID is same`() {
        val b1 = ProposalId(1, "x")
        val b2 = ProposalId(2, "x")
        assertTrue(b2 > b1)
        assertTrue(b1 < b2)
    }

    @Test
    fun `ProposalId should be greater if round is same and ID is greater`() {
        val b1 = ProposalId(1, "x")
        val b2 = ProposalId(1, "y")
        assertTrue(b2 > b1)
        assertTrue(b1 < b2)
    }
}
