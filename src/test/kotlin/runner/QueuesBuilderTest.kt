package runner

import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class QueuesBuilderTest {

    @Test
    fun queueTest() {
        var endCount = 0
        val queues: Queues<String> = QueuesBuilder(String::isEmpty,
            onEnd = { endCount++ },
            namesByPriority = listOf("n1", "n2", "n3"),
            producerCounter = {
                when (it) {
                    "n1" -> 1
                    "n2" -> 2
                    "n3" -> 3
                    else -> 0
                }
            }
        ).build()


        val results = mutableListOf<String>()
        val queuesName = mutableListOf<String>()

        runBlocking {
            GlobalScope.launch {

                queues.send("n2", "n2v1")
                queues.send("n2", "n2v2")
                queues.send("n2", "")

                queues.send("n3", "n3v1")
                queues.send("n3", "n3v2")
                queues.send("n3", "")

                queues.send("n1", "n1_v1")
                queues.send("n1", "n1_v2")
                queues.send("n1", "")

                queues.send("n3", "n3_v1")
                queues.send("n3", "n3_v2")
                queues.send("n3", "")

                queues.send("n2", "n2_v1")
                queues.send("n2", "n2_v2")
                queues.send("n2", "")

                queues.send("n3", "n3__v1")
                queues.send("n3", "n3__v2")
                queues.send("n3", "")
            }

            while (!queues.isTerminated()) {
                val readJob = GlobalScope.launch {
                    queues.forEach { name: String, value: String ->
                        results.add(value)
                        if (!queuesName.contains(name)) {
                            queuesName.add(name)
                        }
                    }
                }
                readJob.join()
                delay(10)
            }
        }

        Assertions.assertEquals(3, queuesName.size)
        Assertions.assertEquals("n1", queuesName[0])
        Assertions.assertEquals("n2", queuesName[1])
        Assertions.assertEquals("n3", queuesName[2])

        Assertions.assertEquals(12, results.size)
        Assertions.assertEquals(1, endCount)
    }

    @Test
    fun queueTestException() {
        val queues: Queues<String> = QueuesBuilder(String::isEmpty,
            onEnd = { println("END") },
            namesByPriority = listOf("n1"),
            producerCounter = {
                when (it) {
                    "n1" -> 1
                    else -> 0
                }
            }
        ).build()

        var ex: IllegalArgumentException? = null
        runBlocking {
            val deferred = GlobalScope.async {
                queues.send("n2", "n2v1")
                queues.send("n1", "")
            }
            try {
                deferred.await()
                Assertions.fail("Should not be here")
            } catch (e: IllegalArgumentException) {
                ex = e
            }
        }
        Assertions.assertNotNull(ex)
    }


}
