package me.ivanyu.paxos.simulation

import kotlin.math.max

class Timer(private val time: Time,
            private val timeout: Long) {

    private val startedAt: Long = time.now()

    fun expired(): Boolean {
        return remain() <= 0
    }

    fun remain(): Long {
        return max(0, (startedAt + timeout) - time.now())
    }
}
