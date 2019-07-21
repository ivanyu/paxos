package me.ivanyu.paxos.simulation

import me.ivanyu.paxos.Accept
import me.ivanyu.paxos.Acceptor
import me.ivanyu.paxos.AcceptorId
import me.ivanyu.paxos.Prepare
import org.apache.logging.log4j.LogManager

class AcceptorActor(private val acceptor: Acceptor): Actor(acceptor.id) {
    private val logger = LogManager.getLogger(this::class.java)

    val id: AcceptorId = acceptor.id

    @Volatile
    private lateinit var network: AcceptorNetwork

    fun attachNetwork(network: AcceptorNetwork) {
        this.network = network
    }

    override fun run() {
        logger.debug("Starting")
        try {
            while (!isInterrupted) {
                val (proposerId, message) = network.poll(this)
                when (message) {
                    is Prepare -> {
                        val promise = acceptor.receivePrepare(message)
                        if (promise != null) {
                            network.sendToProposer(id, proposerId, promise)
                        }
                    }

                    is Accept -> {
                        val accepted = acceptor.receiveAccept(message)
                        if (accepted != null) {
                            network.sendToProposer(id, proposerId, accepted)
                        }
                    }
                }
            }
        } catch (e: InterruptedException) {
            interrupt()
        }

        logger.debug("Stopping")
    }

    override fun toString(): String {
        return "[${acceptor.id}]"
    }
}
