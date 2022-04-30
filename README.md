# Kotlin Free

This repository is an adaptation from a WIP article from [@rlecomte](https://github.com/rlecomte) about Free Monad for process management.
This article is really cool and interesting BTW and I will update this repo when it will be available to link it here.

The article is in Scala and even if I like the language I was thinking that it could be done in Kotlin (but uglier).
Cause I like to hurt myself I decided to do it and this is the result

You can run the project with `./gradlew run`

## Resources

Use Arrow-fx [Resource](https://arrow-kt.io/docs/apidocs/arrow-fx-coroutines/arrow.fx.coroutines/-resource/) instead of `ZManaged`
```
val stack = startZk()
        .zip(startKafka(), startSchemaRegistry())
        { zk, kafka, schema ->
            Triple(zk, kafka, schema)
        }
    stack.use { (zk, kafka, schema) ->
        println("Cool stuff bro")
    }
```

_Output_ :
```
[IO] Start ZooKeeper
[IO] Start Kafka
[IO] Start Schema
Cool stuff bro
[IO] Stop Schema
[IO] Stop Kafka
[IO] Stop ZooKeeper
```

## Free

This part needed to reuse [Lightweight higher-kinded polymorphism](https://www.cl.cam.ac.uk/~jdy22/papers/lightweight-higher-kinded-polymorphism.pdf) previously provided by Arrow.
I also chose to use new Kotlin feature of context receiver to use this to avoid witness by argument.

The example provide two interpreter for `Free[Step, StackConfig]`, one as `List<Event>` and another using `Flow` (We needed to create an integration between Kotlin `Flow` and Arrow `Resource`).

### `List`

```
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
        }
    }
```

_Output_ :
```
[IO] Start ZooKeeper
[IO] Start Kafka
[IO] Start Schema
(ZkRuntime(port=8081), KafkaRuntime(port=8090), SchemaRuntime(port=9092))
[IO] Stop Schema
[IO] Stop Kafka
[IO] Stop ZooKeeper
[Event] StartZookeeker@48503868
[Event] StartKafka@5891e32e
[Event] StartSchema@cb0ed20
[Event] StackStarted(config=(ZkRuntime(port=8081), KafkaRuntime(port=8090), SchemaRuntime(port=9092)))
[Event] StopSchema@8e24743
[Event] StopKafka@74a10858
[Event] StopZookeeker@6895a785
```

### `Flow`

```
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
            var streamInterruptChannel = Channel<Boolean>()
            launch {
                delay(5.seconds)
                streamInterruptChannel.send(true)
            }
            runStream(t, streamInterruptChannel)
                .collect(printEvent)
        }
    }
```

_Output_ :
```
[Event] StartZookeeker@48503868
[IO] Start ZooKeeper
[Event] StartKafka@5891e32e
[IO] Start Kafka
[Event] StartSchema@cb0ed20
[IO] Start Schema
(ZkRuntime(port=8081), KafkaRuntime(port=8090), SchemaRuntime(port=9092))
[Event] StackStarted(config=(ZkRuntime(port=8081), KafkaRuntime(port=8090), SchemaRuntime(port=9092)))
[IO] Stop Schema
[Event] StopSchema@8e24743
[IO] Stop Kafka
[Event] StopKafka@74a10858
[IO] Stop ZooKeeper
[Event] StopZookeeker@6895a785
```