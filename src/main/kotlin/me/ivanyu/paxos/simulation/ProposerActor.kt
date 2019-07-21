package me.ivanyu.paxos.simulation

import me.ivanyu.paxos.Accepted
import me.ivanyu.paxos.Promise
import me.ivanyu.paxos.Proposer
import me.ivanyu.paxos.ProposerId
import org.apache.logging.log4j.LogManager

class ProposerActor(private val roundTripMs: Long,
                    private val time: Time,
                    private val proposer: Proposer): Actor(proposer.id) {
    private val logger = LogManager.getLogger(this::class.java)

    val id: ProposerId = proposer.id

    @Volatile
    private lateinit var network: ProposerNetwork

    fun attachNetwork(network: ProposerNetwork) {
        this.network = network
    }

    private enum class Phase {
        PHASE_1, PHASE_2
    }

    private var phase: Phase = Phase.PHASE_1

    override fun run() {
        logger.debug("Starting")
        try {
            while (!isInterrupted) {
                when (phase) {
                    Phase.PHASE_1 -> phase1()
                    Phase.PHASE_2 -> phase2()
                }
            }
        } catch (e: InterruptedException) {
            interrupt()
        }

        logger.debug("Stopping")
    }

    private fun phase1() {
        val prepare = proposer.nextRound()
        logger.debug("[Phase 1] Starting next round with {}", prepare)
        network.broadcastToAcceptors(id, prepare)

        val waitForPromisesTimeout = roundTripMs * 2
        val waitForPromisesTimer = Timer(time, waitForPromisesTimeout)
        loop@
        while (!waitForPromisesTimer.expired()) {
            logger.debug("[Phase 1] Polling")
            val pollResult = network.poll(this, waitForPromisesTimer.remain()) ?: continue@loop
            logger.debug("[Phase 1] Received {}", pollResult)
            val (acceptorId, message) = pollResult
            when (message) {
                is Promise -> {
                    val accept = proposer.receivePromise(acceptorId, message)
                    if (accept != null) {
                        logger.debug("[Phase 1] Broadcasting {}", accept)
                        network.broadcastToAcceptors(id, accept)
                        phase = Phase.PHASE_2
                        break@loop
                    }
                }

                is Accepted -> { /* ignore */ }
            }
        }
    }

    private fun phase2() {
        val waitForAcceptedTimeout = roundTripMs * 2
        val waitForPromisesTimer = Timer(time, waitForAcceptedTimeout)
        loop@
        while (!waitForPromisesTimer.expired()) {
            logger.debug("[Phase 2] Polling")
            val pollResult = network.poll(this, waitForPromisesTimer.remain()) ?: continue@loop
            logger.debug("[Phase 1] Received {}", pollResult)
            val (acceptorId, message) = pollResult
            when (message) {
                is Promise -> { /* ignore */ }

                is Accepted -> {
                    // The value has been committed.
                    if (proposer.receiveAccepted(acceptorId, message)) {
                        break@loop
                    }
                }
            }
        }
        phase = Phase.PHASE_1
    }

    override fun toString(): String {
        return "[${proposer.id}]"
    }
}
