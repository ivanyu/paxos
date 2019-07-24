package me.ivanyu.paxos.simulation

import me.ivanyu.paxos.AcceptorId
import me.ivanyu.paxos.MessageToAcceptor
import me.ivanyu.paxos.MessageToProposer
import me.ivanyu.paxos.ProposerId
import java.lang.IllegalStateException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

interface AcceptorChannel {
    fun poll(): Pair<ProposerId, MessageToAcceptor>
    fun sendToProposer(from: AcceptorId, to: ProposerId, message: MessageToProposer)
}

interface ProposerChannel {
    fun poll(maxWaitMs: Long): Pair<AcceptorId, MessageToProposer>?
    fun broadcastToAcceptors(from: ProposerId, message: MessageToAcceptor)
}

class Network(private val roundTripMs: Long,
              private val deliverMessageProb: Double,
              private val duplicateMessageProb: Double) {
    private val scheduler = Executors.newScheduledThreadPool(1)

    private inner class AcceptorChannelImpl : AcceptorChannel {
        private val queue = LinkedBlockingQueue<Pair<ProposerId, MessageToAcceptor>>()

        override fun poll(): Pair<ProposerId, MessageToAcceptor> {
            return queue.take()
        }

        override fun sendToProposer(from: AcceptorId, to: ProposerId, message: MessageToProposer) {
            scheduleMessageDelivery {
                this@Network.proposerChannels.getValue(to).deliver(from, message)
            }
        }

        /**
         * Deliver a message from a Proposer to this channel's Acceptor.
         *
         * `Network`'s internal API.
         */
        fun deliver(from: ProposerId, message: MessageToAcceptor) {
            queue.put(Pair(from, message))
        }
    }

    private inner class ProposerChannelImpl : ProposerChannel {
        private val queue = LinkedBlockingQueue<Pair<AcceptorId, MessageToProposer>>()

        override fun poll(maxWaitMs: Long): Pair<AcceptorId, MessageToProposer>? {
            return queue.poll(maxWaitMs, TimeUnit.MILLISECONDS)
        }

        override fun broadcastToAcceptors(from: ProposerId, message: MessageToAcceptor) {
            this@Network.acceptorChannels.forEach {
                scheduleMessageDelivery { it.value.deliver(from, message) }
            }
        }

        /**
         * Deliver a message from a Acceptor to this channel's Proposer.
         *
         * `Network`'s internal API.
         */
        fun deliver(from: AcceptorId, message: MessageToProposer) {
            queue.put(Pair(from, message))
        }
    }

    fun getAcceptorChannel(acceptorActor: AcceptorActor): AcceptorChannel {
        if (acceptorActor.id in acceptorChannels) {
            throw IllegalStateException("Acceptor already attached")
        }
        val channel = AcceptorChannelImpl()
        acceptorChannels[acceptorActor.id] = channel
        return channel
    }

    fun getProposerChannel(proposerActor: ProposerActor): ProposerChannel {
        if (proposerActor.id in proposerChannels) {
            throw IllegalStateException("Proposer already attached")
        }
        val channel = ProposerChannelImpl()
        proposerChannels[proposerActor.id] = channel
        return channel
    }

    private val proposerChannels: MutableMap<AcceptorId, ProposerChannelImpl> = mutableMapOf()
    private val acceptorChannels: MutableMap<AcceptorId, AcceptorChannelImpl> = mutableMapOf()

    private fun scheduleMessageDelivery(deliveryAction: () -> Unit) {
        try {
            val shouldDeliverMessage = Random.nextDouble() < deliverMessageProb
            if (shouldDeliverMessage) {
                scheduler.schedule(deliveryAction, Random.nextLong(roundTripMs), TimeUnit.MILLISECONDS)

                val shouldDeliverDuplicate = Random.nextDouble() < duplicateMessageProb
                if (shouldDeliverDuplicate) {
                    scheduler.schedule(deliveryAction, Random.nextLong(roundTripMs), TimeUnit.MILLISECONDS)
                }
            }
        } catch (e: RejectedExecutionException) {
            // It's OK to ignore the exception.
            // It means the network in shutting down and the delivery is no longer needed.
        }
    }

    fun shutdown() {
        scheduler.shutdown()
    }
}
