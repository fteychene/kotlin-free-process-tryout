import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.release
import arrow.fx.coroutines.resource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

data class ZkRuntime(val port: Int)
data class KafkaRuntime(val port: Int)
data class SchemaRuntime(val port: Int)

sealed class Event
object StartZookeeker: Event()
object StopZookeeker: Event()
object StartKafka: Event()
data class StackStarted(val config: StackConfig): Event()
object StopKafka: Event()
object StartSchema: Event()
object StopSchema: Event()

fun startZk(): Resource<ZkRuntime> = resource {
    printIo("Start ZooKeeper")
    ZkRuntime(8081)
} release {
    printIo("Stop ZooKeeper")
}

fun startZkStep(): Step<ZkRuntime> = Step(
    run = startZk(),
    startedEvent = StartZookeeker,
    stoppingEvent = StopZookeeker
)

fun startKafka(): Resource<KafkaRuntime> = resource {
    printIo("Start Kafka")
    KafkaRuntime(8090)
} release {
    printIo("Stop Kafka")
}

fun startKafkaStep(): Step<KafkaRuntime> = Step(
    run = startKafka(),
    startedEvent = StartKafka,
    stoppingEvent = StopKafka
)

fun startSchemaRegistry(): Resource<SchemaRuntime> = resource {
    printIo("Start Schema")
    SchemaRuntime(9092)
} release {
    printIo("Stop Schema")
}

fun startSchemaRegistryStep(): Step<SchemaRuntime> = Step(
    run = startSchemaRegistry(),
    startedEvent = StartSchema,
    stoppingEvent = StopSchema
)

class ForStep { private constructor()}
typealias StepOf<A> = Kind<ForStep, A>
fun <A> StepOf<A>.fix(): Step<A> = this as Step<A>

data class Step<out A> (
    val run: Resource<A>,
    val startedEvent: Event,
    val stoppingEvent: Event
): StepOf<A>  {
    companion object {
        fun functor(): Functor<ForStep> = object: Functor<ForStep> {
            override fun <A, B> Kind<ForStep, A>.map(f: (A) -> B): Kind<ForStep, B> = with(fix()) {
                Step(
                    run =  run.map(f),
                    startedEvent,
                    stoppingEvent
                )
            }

        }
    }
}

typealias StackConfig = Triple<ZkRuntime, KafkaRuntime, SchemaRuntime>

suspend fun runList(program: FreeOf<ForStep, StackConfig>, interrupt: Channel<Boolean>): List<Event> {
    suspend fun FreeOf<ForStep, StackConfig>.fold(): List<Event> =
        when(val f = this.fix()) {
            is Pure -> { interrupt.receive(); listOf(StackStarted(f.value)) }
            is Suspend -> f.next.fix().run {
                listOf(startedEvent) + run.use { it.fold() } + listOf(stoppingEvent)
            }
        }

    return program.fold()
}

fun <T> Resource<T>.asFlow(): Flow<T> = let { resource ->
    flow {
        resource.use {
            emit(it)
        }
    }
}

suspend fun runStream(program: FreeOf<ForStep, StackConfig>, interrupt: Channel<Boolean>): Flow<Event> {
    suspend fun FreeOf<ForStep, StackConfig>.fold(): Flow<Event> =
        when(val f = this.fix()) {
            is Pure -> flow { emit(StackStarted(f.value)); interrupt.receive() }
            is Suspend -> f.next.fix().run { flow {
                emit(startedEvent)
                emitAll(run.asFlow().flatMapConcat { it.fold() })
                emit(stoppingEvent)
            }}
        }

    return program.fold()
}

fun main() = runBlocking {
    println("============= Resources ===============")
    val stack = startZk()
        .zip(startKafka(), startSchemaRegistry())
        { zk, kafka, schema ->
            Triple(zk, kafka, schema)
        }
    stack.use { (zk, kafka, schema) ->
        println("Cool stuff bro")
    }

    println("=============== Free =================")
    with(Step.functor()) {
        with(freeMonad()) {
            val t = startZkStep().liftF()
                .flatMap { zkRuntime ->
                    startKafkaStep().liftF()
                        .flatMap { kafkaRuntime ->
                            startSchemaRegistryStep()
                                .map { schemaRuntime ->
                                    Triple(zkRuntime, kafkaRuntime, schemaRuntime).also(::println)
                                }
                                .liftF()
                        }
                }
            println(t)
            println("----- List -----")
            var listInterruptChannel = Channel<Boolean>()
            launch {
                delay(5.seconds)
                listInterruptChannel.send(true)
            }
            runList(t, listInterruptChannel)
                .forEach(printEvent)
            println("----- Stream -----")
            var streamInterruptChannel = Channel<Boolean>()
            launch {
                delay(5.seconds)
                streamInterruptChannel.send(true)
            }
            runStream(t, streamInterruptChannel)
                .collect(printEvent)
        }
    }
}

val printEvent = { v: Any? -> println("[Event] $v") }
val printIo = { v: Any? -> println("[IO] $v") }