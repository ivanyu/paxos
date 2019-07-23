package me.ivanyu.paxos.simulation

import me.ivanyu.paxos.ProposalId
import me.ivanyu.paxos.Value
import org.apache.logging.log4j.LogManager
import kotlin.collections.ArrayList

sealed class Event
data class DiskWrite(val diskId: String,
                     val promisedProposalId: ProposalId?,
                     val acceptedProposalId: ProposalId?,
                     val acceptedValue: Value?): Event() {
    override fun toString(): String {
        return "DiskWrite($diskId, promised=$promisedProposalId, accepted=($acceptedProposalId,$acceptedValue))"
    }
}

class GlobalObserver(private val quorumSize: Int) {
    private val logger = LogManager.getLogger()

    private val eventLog: MutableList<Event> = mutableListOf()
    private val latestEventPerDisk: MutableMap<String, Pair<ProposalId?, Value?>> = mutableMapOf()

    var commitmentWasBroken = false
        private set(value) { field = value }
        @Synchronized
        get() = field
    var commitmentBrokenAtIdx = -1
        private set(value) { field = value }
        @Synchronized
        get() = field

    var wasCommitted = false
        private set(value) { field = value }
        @Synchronized
        get() = field
    var committedAtIdx = -1
        private set(value) { field = value }
        @Synchronized
        get() = field

    private lateinit var committedValue: Value
    private lateinit var committedProposalId: ProposalId

    @Synchronized
    fun addEvent(event: Event) {
        eventLog.add(event)
        when (event) {
            is DiskWrite -> processDiskWrite(event)
        }
    }

    private fun processDiskWrite(event: DiskWrite) {
        if (!commitmentWasBroken) {
            latestEventPerDisk[event.diskId] = Pair(event.acceptedProposalId, event.acceptedValue)
        }

        val quorumProposalIdAndValue = quorumProposalIdAndValue()
        if (!wasCommitted) {
            if (quorumProposalIdAndValue != null) {
                wasCommitted = true
                committedAtIdx = eventLog.size - 1
                committedProposalId = quorumProposalIdAndValue.first
                committedValue = quorumProposalIdAndValue.second
                logger.debug("Value {} committed at {}", committedValue, committedAtIdx)
            }
        } else if (!commitmentWasBroken && quorumProposalIdAndValue != null) {
            if (quorumProposalIdAndValue.first != committedProposalId
                    && quorumProposalIdAndValue.second != committedValue) {
                commitmentWasBroken = true
                commitmentBrokenAtIdx = eventLog.size - 1
                logger.debug("Commitment broken at {}", commitmentBrokenAtIdx)
            }
        }
    }

    private fun quorumProposalIdAndValue(): Pair<ProposalId, Value>? {
        val quorumValues = latestEventPerDisk.values
                .filter { it.first != null && it.second != null }
                .groupBy { it }
                .values.find { it.size >= quorumSize }
        val p = quorumValues?.get(0)
        if (p != null) {
            return Pair(p.first!!, p.second!!)
        } else {
            return null
        }
    }

    @Synchronized
    fun eventLog(): List<Event> {
        return ArrayList(eventLog)
    }

    @Synchronized
    fun committedValue(): Value {
        return committedValue
    }
}
