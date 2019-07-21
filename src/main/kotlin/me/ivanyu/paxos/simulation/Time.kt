package me.ivanyu.paxos.simulation

interface Time {
    fun now(): Long
}

class SystemTime : Time {
    override fun now(): Long {
        return System.currentTimeMillis()
    }

}
