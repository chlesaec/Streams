package runner

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking


class QueueItems<T>(val name: String,
                    val nbeProducer: Int,
                    val isEndProducer: (T) -> Boolean) {

    private val internalChannel = Channel<Pair<String, T>>(capacity = 10)

    private var endProducerCounter = 0

    fun send(item: T) {
        runBlocking {
            internalChannel.send(Pair(this@QueueItems.name, item))
        }
    }

    suspend fun forEach(f: (T) -> Unit) {
        for (pairElem in internalChannel) {
            if (isEndProducer(pairElem.second)) {
                this.endProducerCounter++
            }
            f(pairElem.second)
            if (this.isTerminated()) {
                this.internalChannel.close()
            }
        }
    }

    fun isTerminated() : Boolean = this.endProducerCounter >= this.nbeProducer
}

class Queues<T>(private val isEndProducer: (T) -> Boolean,
                private val internalQueues: List<QueueItems<T>>,
                private val onEnd: (T) -> Unit) {

    private var currentQueue = 0

    fun send(queueName: String, item: T) {
        val namedQueue = this.internalQueues
            .find { it.name == queueName }
        if (namedQueue == null) {
            throw IllegalArgumentException("No queue with name ${queueName}")
        }
        namedQueue.send(item)
    }

    /**
     * Consume current queue.
     */
    suspend fun forEach(f: (String, T) -> Unit) {
        while (!this.isTerminated()) {
            val inQueue = internalQueues[currentQueue]
            inQueue.forEach{
                if (!this.isEndProducer(it)) {
                    f(inQueue.name, it)
                }
                else if (inQueue.isTerminated()) {
                        this.currentQueue++
                        if (this.isTerminated()) {
                            this.onEnd(it)
                        }
                    }

            }
        }
    }

    fun isTerminated() : Boolean = this.currentQueue >= this.internalQueues.size
}

class QueuesBuilder<T>(val isEndProducer: (T) -> Boolean,
                       val onEnd: (T) -> Unit = {},
                       val namesByPriority: List<String> = emptyList(),
                       val producerCounter : (String) -> Int = {1}) {

    fun build() : Queues<T> {
        val names = if (namesByPriority.isEmpty()) {
            listOf("*")
        }
        else {
            namesByPriority
        }
        val internalQueues: List<QueueItems<T>> = names.map {
            val nbeProducer: Int = producerCounter(it)
            QueueItems(it, nbeProducer, isEndProducer)
        }

        return Queues(isEndProducer, internalQueues, onEnd)
    }
}
