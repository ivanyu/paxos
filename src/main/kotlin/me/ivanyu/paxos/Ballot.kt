package me.ivanyu.paxos

import java.util.Objects

data class Ballot(val round: Int, val nodeId: String) : Comparable<Ballot> {

    override fun compareTo(other: Ballot): Int {
        Objects.requireNonNull(other)

        if (round < other.round) {
            return -1
        }

        if (round > other.round) {
            return 1
        }

        return nodeId.compareTo(other.nodeId)
    }
}
