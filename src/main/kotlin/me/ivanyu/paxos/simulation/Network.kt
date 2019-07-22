package me.ivanyu.paxos.simulation

import me.ivanyu.paxos.AcceptorId
import me.ivanyu.paxos.MessageToAcceptor
import me.ivanyu.paxos.MessageToProposer
import me.ivanyu.paxos.ProposerId
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

interface ProposerNetwork {
    fun poll(to: ProposerActor, maxWaitMs: Long): Pair<AcceptorId, MessageToProposer>?
    fun broadcastToAcceptors(from: ProposerId, message: MessageToAcceptor)
}

interface AcceptorNetwork {
    fun poll(to: AcceptorActor): Pair<ProposerId, MessageToAcceptor>
    fun sendToProposer(from: AcceptorId, to: ProposerId, message: MessageToProposer)
}

class Network(acceptorActors: List<AcceptorActor>,
              proposerActors: List<ProposerActor>,
              private val roundTripMs: Long,
              private val deliverMessageProb: Double,
              private val duplicateMessageProb: Double) : ProposerNetwork, AcceptorNetwork {
    private val scheduler = Executors.newScheduledThreadPool(1)

    private val acceptorQueues: Map<AcceptorId, LinkedBlockingQueue<Pair<ProposerId, MessageToAcceptor>>> = acceptorActors.map {
        it.id to LinkedBlockingQueue<Pair<ProposerId, MessageToAcceptor>>()
    }.toMap()

    private val proposerQueues: Map<ProposerId, LinkedBlockingQueue<Pair<AcceptorId, MessageToProposer>>> = proposerActors.map {
        it.id to LinkedBlockingQueue<Pair<AcceptorId, MessageToProposer>>()
    }.toMap()

    override fun poll(to: AcceptorActor): Pair<ProposerId, MessageToAcceptor> {
        return acceptorQueues.getValue(to.id).take()
    }

    override fun sendToProposer(from: AcceptorId, to: ProposerId, message: MessageToProposer) {
        try {
            if (shouldDeliverMessage()) {
                val sendFunc = {
                    proposerQueues.getValue(to).put(Pair(from, message))
                }
                scheduler.schedule(sendFunc, Random.nextLong(roundTripMs), TimeUnit.MILLISECONDS)

                if (shouldDeliverDuplicate()) {
                    scheduler.schedule(sendFunc, Random.nextLong(roundTripMs), TimeUnit.MILLISECONDS)
                }
            }
        } catch (e: RejectedExecutionException) {
            // it's ok to silently ignore it
        }
    }

    override fun poll(to: ProposerActor, maxWaitMs: Long): Pair<AcceptorId, MessageToProposer>? {
        return proposerQueues.getValue(to.id).poll(maxWaitMs, TimeUnit.MILLISECONDS)
    }

    override fun broadcastToAcceptors(from: ProposerId, message: MessageToAcceptor) {
        acceptorQueues.forEach {
            try {
                if (shouldDeliverMessage()) {
                    val sendFunc = {
                        it.value.put(Pair(from, message))
                    }
                    scheduler.schedule(sendFunc, Random.nextLong(roundTripMs), TimeUnit.MILLISECONDS)

                    if (shouldDeliverDuplicate()) {
                        scheduler.schedule(sendFunc, Random.nextLong(roundTripMs), TimeUnit.MILLISECONDS)
                    }
                }
            } catch (e: RejectedExecutionException) {
                // it's ok to silently ignore it
            }
        }
    }

    private fun shouldDeliverMessage(): Boolean {
        return Random.nextDouble() < deliverMessageProb
    }

    private fun shouldDeliverDuplicate(): Boolean {
        return Random.nextDouble() < duplicateMessageProb
    }

    fun shutdown() {
        scheduler.shutdown()
    }
}
