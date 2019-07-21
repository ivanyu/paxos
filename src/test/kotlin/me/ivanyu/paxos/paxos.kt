package me.ivanyu.paxos

import org.mockito.ArgumentMatchers.same

// Avoid Kotlin's null checker
fun <T> sameNonNull(value: T): T {
    same(value)
    return value
}
