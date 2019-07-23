package me.ivanyu.paxos

import org.apache.logging.log4j.LogManager
import java.lang.IllegalStateException

class Proposer(val id: ProposerId,
               private val value: Value,
               private val quorumSize: Int,
               private val cheat: Boolean) {
    private val logger = LogManager.getLogger()

    var round: Int = -1
        private set(value) { field = value }
    private fun currentProposalId() = ProposalId(round, id)

    fun roundStarted(): Boolean = round >= 0

    private var receivedPromises: MutableSet<Pair<AcceptorId, Promise>> = mutableSetOf()
    private var receivedAccepted: MutableSet<Pair<AcceptorId, Accepted>> = mutableSetOf()

    fun nextRound(): Prepare {
        round += 1
        receivedPromises.clear()
        receivedAccepted.clear()
        return Prepare(currentProposalId())
    }

    fun receivePromise(acceptorId: AcceptorId, promise: Promise): Accept? {
        if (!roundStarted()) {
            throw IllegalStateException("Round is not started")
        }

        assert(promise.promisedProposalId.proposerId == id) { "Incorrect Proposer ID" }
        if (promise.promisedProposalId != currentProposalId()) {
            logger.debug("Ignoring {} as the current proposal ID is {}", promise, currentProposalId())
            return null
        }

        receivedPromises.add(Pair(acceptorId, promise))

        if (receivedPromises.size < quorumSize) {
            return null
        }

        if (cheat) {
            return Accept(currentProposalId(), value)
        } else {
            val maxAccepted = receivedPromises
                    .map { it.second }
                    .filter { it.acceptedProposalId != null }
                    .maxBy { it.acceptedProposalId!! }
            val valueToAccept = maxAccepted?.acceptedValue ?: value
            return Accept(currentProposalId(), valueToAccept)
        }
    }

    /**
     * @return `true` if the value is committed after the received `Accept`.
     */
    fun receiveAccepted(acceptorId: AcceptorId, accepted: Accepted): Boolean {
        if (!roundStarted()) {
            throw IllegalStateException("Round is not started")
        }

        assert(accepted.proposalId.proposerId == id) { "Incorrect Proposer ID" }
        if (accepted.proposalId != currentProposalId()) {
            logger.debug("Ignoring {} as the current proposal ID is {}", accepted, currentProposalId())
        } else {
            receivedAccepted.add(Pair(acceptorId, accepted))
        }
        return receivedAccepted.size >= quorumSize
    }
}
