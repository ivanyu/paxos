package me.ivanyu.paxos

data class Promise(val minBallot: Ballot, val acceptedBallot: Ballot?, val acceptedValue: String?)
