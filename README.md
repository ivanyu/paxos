# Paxos

This repository is an implementation of basic single-decree Paxos
algorithm.

It contains logic classes of `Acceptor` and `Proposer` that are tested
on the unit level.

It also contains a simulator with time and message passing that can
detect breaking of safety guarantees.

To run a simulation, execute:
```bash
./gradlew runSimulation -Pruns=10000
```

When the safety guarantee is detected to be broken, the simulation stop
and the log of events is printed.

Check
[src/main/resources/simulation.conf](src/main/resources/simulation.conf)
for parameters of the simulation.

# LICENSE

Copyright 2019 Ivan Yurchenko

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
