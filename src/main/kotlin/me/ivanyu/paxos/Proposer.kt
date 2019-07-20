package me.ivanyu.paxos

import java.lang.IllegalStateException

class Proposer(private val id: ProposerId, private val value: Value, private val quorumSize: Int) {
    private var round: Int = -1
    private fun currentProposalId() = ProposalId(round, id)

    fun roundStarted(): Boolean = round >= 0

    private var receivedPromises: MutableSet<Pair<AcceptorId, Promise>> = mutableSetOf()

    fun nextRound(): Prepare {
        round += 1
        receivedPromises.clear()
        return Prepare(currentProposalId())
    }

    fun receivePromise(acceptorId: AcceptorId, promise: Promise): Accept? {
        if (!roundStarted()) {
            throw IllegalStateException("Round is not started")
        }

        assert(promise.promisedProposalId.proposerId == id) { "Incorrect Proposer ID" }
        if (promise.promisedProposalId != currentProposalId()) {
            // log ignoring promises other than with the current proposal ID
        }

        receivedPromises.add(Pair(acceptorId, promise))

        if (receivedPromises.size < quorumSize) {
            return null
        }

        val maxAccepted = receivedPromises
                .map { it.second }
                .filter { it.acceptedProposalId != null }
                .maxBy { it.acceptedProposalId!! }
        val valueToAccept = maxAccepted?.acceptedValue ?: value
        return Accept(currentProposalId(), valueToAccept)
    }
}
