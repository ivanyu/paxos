package me.ivanyu.paxos.simulation

import me.ivanyu.paxos.*
import org.apache.logging.log4j.LogManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.random.Random

const val roundTripMs = 10L
const val deliverMessageProb = 0.7
const val cheatingProposers = false
const val acceptorN = 3
const val proposerN = 3

private val time = SystemTime()
private val quorumSize: Int = ceil(acceptorN.toDouble() / 2).toInt()

fun main(args: Array<String>) {
    val logger = LogManager.getLogger("main")
    val output = true
//    val waitAfterCommittedMs = roundTripMs * 30
    val waitAfterCommittedMs = roundTripMs * 5
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
        val commitmentBroken = run(n, waitAfterCommittedMs, output)
        if (commitmentBroken) {
            commitmentBrokenCount += 1
            break
        }
    }
    logger.info("Commitment was broken {} / {}", commitmentBrokenCount, runs)
}

/**
 * Runs a simulation.
 *
 * @return `true` if the commitment to a value was broken at some point.
 */
private fun run(runN: Int, waitAfterCommittedMs: Long, output: Boolean): Boolean {
    val observer = GlobalObserver(quorumSize)

    val acceptorActors = (0 until acceptorN).map {
        val id = "ACCEPTOR_$it"
        val disk = AcceptorDiskImpl(id, observer)
        val acceptor = Acceptor(id, disk)
        AcceptorActor(acceptor)
    }

    val proposerActors = (0 until proposerN).map {
        val proposer = Proposer("PROPOSER_$it", "value_$it", quorumSize, cheatingProposers)
        ProposerActor(roundTripMs, time, proposer)
    }
    val network = Network(acceptorActors, proposerActors, roundTripMs, deliverMessageProb)

    acceptorActors.forEach { it.attachNetwork(network) }
    proposerActors.forEach { it.attachNetwork(network) }

    acceptorActors.forEach { it.start() }
    val scheduler = Executors.newScheduledThreadPool(1)
    proposerActors.forEach { scheduler.schedule(it::start, Random.nextLong(2 * roundTripMs), TimeUnit.MILLISECONDS) }
    scheduler.shutdown() // will shutdown after all scheduled tasks are executed

    while (!observer.wasCommitted) {
        Thread.sleep(10)
    }
    // Give room for errors.
    Thread.sleep(waitAfterCommittedMs)

    // Shutdown simulation
    network.shutdown()
    proposerActors.forEach { it.interrupt() }
    acceptorActors.forEach { it.interrupt() }
    proposerActors.forEach { it.join() }
    acceptorActors.forEach { it.join() }

    if (output) {
        if (observer.commitmentWasBroken) {
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
    }
    return observer.commitmentWasBroken
}
