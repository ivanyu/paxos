package me.ivanyu.paxos

import java.util.Objects

typealias Value = String
typealias ProposerId = String
typealias AcceptorId = String

interface MessageToAcceptor
data class Prepare(val proposalId: ProposalId) : MessageToAcceptor
data class Accept(val proposalId: ProposalId, val value: Value) : MessageToAcceptor

interface MessageToProposer
data class Promise(val promisedProposalId: ProposalId,
                   val acceptedProposalId: ProposalId?,
                   val acceptedValue: Value?) : MessageToProposer
data class Accepted(val proposalId: ProposalId,
                    val value: Value) : MessageToProposer

data class ProposalId(val round: Int, val proposerId: ProposerId) : Comparable<ProposalId> {

    override fun compareTo(other: ProposalId): Int {
        Objects.requireNonNull(other)

        if (round < other.round) {
            return -1
        }

        if (round > other.round) {
            return 1
        }

        return proposerId.compareTo(other.proposerId)
    }
}
