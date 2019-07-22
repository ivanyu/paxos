package me.ivanyu.paxos.simulation

import me.ivanyu.paxos.AcceptorDisk
import me.ivanyu.paxos.ProposalId
import me.ivanyu.paxos.Value

class AcceptorDiskImpl(val id: String,
                       val observer: GlobalObserver): AcceptorDisk {

    var acceptedProposalId: ProposalId? = null
    var acceptedValue: Value? = null

    override fun write(promisedProposalId: ProposalId?, acceptedProposalId: ProposalId?, acceptedValue: Value?) {
        val sameProposalIdAndValue = this.acceptedProposalId == acceptedProposalId
                && this.acceptedValue == acceptedValue
        this.acceptedProposalId = acceptedProposalId
        this.acceptedValue = acceptedValue
//        if (!sameProposalIdAndValue) {
            observer.addEvent(DiskWrite(id, promisedProposalId, acceptedProposalId, acceptedValue))
//        }
    }
}
