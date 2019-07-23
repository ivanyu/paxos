package me.ivanyu.paxos.simulation

import com.typesafe.config.ConfigFactory
import me.ivanyu.paxos.Acceptor
import me.ivanyu.paxos.Proposer
import org.apache.logging.log4j.LogManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class Simulation(private val cheatingProposers: Boolean,
                 private val outputIfCommitmentWasBroken: Boolean) {
    private val logger = LogManager.getLogger()

    private val time = SystemTime()

    private val conf = ConfigFactory.load("simulation.conf")
    private val acceptorsFrom = conf.getInt("acceptors.from")
    private val acceptorsTo = conf.getInt("acceptors.to")
    private val proposersFrom = conf.getInt("proposers.from")
    private val proposersTo = conf.getInt("proposers.to")
    private val roundTripMsFrom = conf.getInt("network.round-trip-ms.from")
    private val roundTripMsTo = conf.getInt("network.round-trip-ms.to")
    private val deliverMessageProbFrom = conf.getDouble("network.deliver-message-probability.from")
    private val deliverMessageProbTo = conf.getDouble("network.deliver-message-probability.to")
    private val duplicateMessageProbFrom = conf.getDouble("network.duplicate-message-probability.from")
    private val duplicateMessageProbTo = conf.getDouble("network.duplicate-message-probability.to")

    /**
     * Runs a simulation.
     *
     * @return `true` if the commitment to a value was broken at some point.
     */
    fun run(): Boolean {
        val acceptorsN = Random.nextInt(acceptorsFrom, acceptorsTo + 1)
        val proposersN = Random.nextInt(proposersFrom, proposersTo + 1)
        val quorumSize: Int = acceptorsN / 2 + 1

        val roundTripMs = Random.nextLong(roundTripMsFrom.toLong(), roundTripMsTo.toLong() + 1)
//        val waitAfterCommittedMs = roundTripMs * 30
        val waitAfterCommittedMs = roundTripMs * 5
        val deliverMessageProb = Random.nextDouble(deliverMessageProbFrom, deliverMessageProbTo)
        val duplicateMessageProb = Random.nextDouble(duplicateMessageProbFrom, duplicateMessageProbTo)

        logger.info("acceptorsN={}, proposersN={} quorumSize={} "
                + "roundTripMs={} waitAfterCommittedMs={} deliverMessageProb={} duplicateMessageProb={}",
                acceptorsN, proposersN, quorumSize,
                roundTripMs, waitAfterCommittedMs, deliverMessageProb, duplicateMessageProb)

        val observer = GlobalObserver(quorumSize)

        val acceptorActors = (0 until acceptorsN).map {
            val id = "ACCEPTOR_$it"
            val disk = AcceptorDiskImpl(id, observer)
            val acceptor = Acceptor(id, disk)
            AcceptorActor(acceptor)
        }

        val proposerActors = (0 until proposersN).map {
            val proposer = Proposer("PROPOSER_$it", "value_$it", quorumSize, cheatingProposers)
            ProposerActor(roundTripMs, time, proposer)
        }
        val network = Network(roundTripMs, deliverMessageProb, duplicateMessageProb)

        acceptorActors.forEach {
            val channel = network.getAcceptorChannel(it)
            it.attachChannel(channel)
        }
        proposerActors.forEach {
            val channel = network.getProposerChannel(it)
            it.attachChannel(channel)
        }

        // Concurrency note:
        // All previous stuff including network init happens-before thread starts.
        acceptorActors.forEach { it.start() }
        val scheduler = Executors.newScheduledThreadPool(1)
        proposerActors.forEach { scheduler.schedule(it::start, Random.nextLong(2 * roundTripMs), TimeUnit.MILLISECONDS) }
        scheduler.shutdown() // will shutdown after all scheduled tasks are executed

        while (!observer.wasCommitted) {
            Thread.sleep(10)
        }
        // Give room for commitment breaking errors to appear.
        Thread.sleep(waitAfterCommittedMs)

        // Shutdown simulation.
        network.shutdown()
        proposerActors.forEach { it.interrupt() }
        acceptorActors.forEach { it.interrupt() }
        proposerActors.forEach { it.join() }
        acceptorActors.forEach { it.join() }

        if (outputIfCommitmentWasBroken && observer.commitmentWasBroken) {
            println(observer.committedValue())
            observer.eventLog().forEachIndexed { i, event ->
                if (i == observer.committedAtIdx) {
                    print("--> ")
                } else if (i == observer.commitmentBrokenAtIdx) {
                    print("-X- ")
                } else {
                    print("    ")
                }

                println(event)
            }
        }
        return observer.commitmentWasBroken
    }
}

fun main(args: Array<String>) {
    val logger = LogManager.getLogger("main")

    val cheatingProposers = false
    val outputIfCommitmentWasBroken = true
    val simulation = Simulation(cheatingProposers, outputIfCommitmentWasBroken)

    val runs: Int =
            if (args.size == 1) {
                args[0].toInt()
            } else {
                10000
            }
    var commitmentBrokenCount = 0
    logger.info("Making {} runs", runs)
    for (n in 0 until runs) {
        logger.info("Run {}", n)
        val commitmentBroken = simulation.run()
        if (commitmentBroken) {
            commitmentBrokenCount += 1
            break
        }
    }
    logger.info("Commitment was broken {} / {}", commitmentBrokenCount, runs)
}
