package me.ivanyu.paxos

class Acceptor {
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
        // TODO should it accept if hasn't promisedProposalId at all?

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
