package me.ivanyu.paxos

class Acceptor {
    private var minBallot: Ballot? = null // TODO better name

    private var acceptedBallot: Ballot? = null
    private var acceptedValue: String? = null

    internal fun prepare(ballot: Ballot): Promise? {
        if (minBallot != null && minBallot!! > ballot) {
            return null
        }

        minBallot = ballot
        return Promise(minBallot!!, acceptedBallot, acceptedValue)
    }

    internal fun accept(ballot: Ballot, value: String): Ballot {
        // TODO should it accept if hasn't promised at all?

        if (minBallot == null || ballot >= minBallot!!) {
            minBallot = ballot
            acceptedBallot = ballot
            acceptedValue = value
        }
        return minBallot!!
    }
}
