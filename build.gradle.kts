plugins {
    id("org.jetbrains.kotlin.jvm").version("1.3.10")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    jcenter()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.apache.logging.log4j:log4j-api:2.12.0")
    implementation("org.apache.logging.log4j:log4j-core:2.12.0")
    implementation("com.typesafe:config:1.3.4")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.5.0")
    testImplementation("org.mockito:mockito-core:3.0.0")
    testImplementation("org.mockito:mockito-junit-jupiter:3.0.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("runSimulation", JavaExec::class) {
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    main = "me.ivanyu.paxos.simulation.SimulationKt"
    if (project.hasProperty("runs")) {
        args(project.property("runs"))
    }
}
