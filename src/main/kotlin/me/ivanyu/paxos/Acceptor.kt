package me.ivanyu.paxos

class Acceptor(val id: AcceptorId) {
    private var promisedProposalId: ProposalId? = null

    private var acceptedProposalId: ProposalId? = null
    private var acceptedValue: String? = null

    internal fun receivePrepare(prepare: Prepare): Promise? {
        if (promisedProposalId != null && promisedProposalId!! > prepare.proposalId) {
            return null
        }

        // It's OK to receive duplicate Prepare.
        promisedProposalId = prepare.proposalId
        return Promise(promisedProposalId!!, acceptedProposalId, acceptedValue)
    }

    internal fun receiveAccept(accept: Accept): Accepted? {
        if (promisedProposalId == null || accept.proposalId >= promisedProposalId!!) {
            promisedProposalId = accept.proposalId
            acceptedProposalId = accept.proposalId
            acceptedValue = accept.value
            return Accepted(acceptedProposalId!!, acceptedValue!!)
        } else {
            return null
        }
    }
}
