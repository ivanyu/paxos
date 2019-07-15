package me.ivanyu.paxos

class Acceptor(val disk: AcceptorDisk) {
    private var promisedProposalId: ProposalId? = null

    private var acceptedProposalId: ProposalId? = null
    private var acceptedValue: Value? = null

    internal fun receivePrepare(prepare: Prepare): Promise? {
        if (promisedProposalId != null && promisedProposalId!! > prepare.proposalId) {
            return null
        }

        // It's OK to receive duplicate Prepare.
        promisedProposalId = prepare.proposalId
        writeState()
        return Promise(promisedProposalId!!, acceptedProposalId, acceptedValue)
    }

    internal fun receiveAccept(accept: Accept): Accepted? {
        if (promisedProposalId == null || accept.proposalId >= promisedProposalId!!) {
            promisedProposalId = accept.proposalId
            acceptedProposalId = accept.proposalId
            acceptedValue = accept.value
            writeState()
            return Accepted(acceptedProposalId!!, acceptedValue!!)
        } else {
            return null
        }
    }

    private fun writeState() {
        disk.write(promisedProposalId, acceptedProposalId, acceptedValue)
    }
}
