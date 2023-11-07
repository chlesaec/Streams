package runner

import connectors.Connector
import functions.InputItem
import graph.Node
import job.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.collections.HashMap

interface Runner {
    fun compile(job: Job) : () -> Unit
}

class EndRunner(val identifier : UUID)

class LinkedRunnerItem(
    val link: JobLink?,
    val runner : RunnerItemChannel)

class InputQueue(val pattern: String,
                 val nbeProducer: Int) {
    val inputQueue = Channel<Pair<String, InputItem>> {}

    var endProducer = 0

    suspend fun send(branch: String, item: InputItem) {
        inputQueue.send(Pair(branch, item))
    }

    suspend fun forEach(f: (InputItem) -> Unit) {
        for (pairElem in inputQueue) {
            if (pairElem.second.input is EndRunner) {
                this.endProducer++
            }
            f(pairElem.second)
        }
    }

    fun isTerminated() : Boolean = this.endProducer >= this.nbeProducer
}

class InputQueues {
    val inputQueues: List<InputQueue>

    val onEnd: (EndRunner) -> Unit

    var currentQueue = 0

    constructor(
        item: RunnerItemChannel,
        onEnd: (EndRunner) -> Unit
    ) {
        val priorities = item.connector.inputPriority()
        this.onEnd = onEnd

        this.inputQueues = if (priorities.isEmpty()) {
            val nbePred = if (item.precedessors.isEmpty()) 1 else item.precedessors.size
            listOf(InputQueue("*", nbePred))
        } else {
            priorities.map {
                pattern: String ->
                val count = item.precedessors
                    .filter {
                        pattern == "*" ||
                                (it.link != null && it.link.endName == pattern)
                    }
                    .count()
                InputQueue(pattern , count)
            }
        }
    }

    suspend fun send(branch: JobLinkData?, item: InputItem) {
        val nextName = branch?.endName ?: "*"
        this.inputQueues.find { it.pattern == nextName }
        val queue: InputQueue? = this.inputQueues.find { it.pattern == nextName }
        if (queue == null) {
            throw IllegalArgumentException("Queue ${nextName} does not exists")
        }
        queue.send(nextName, item)
    }

    /**
     * Consume current queue.
     */
    suspend fun forEach(f: (InputItem) -> Unit) {
        if (!this.isTerminated()) {
            val inQueue = inputQueues[currentQueue]
            inQueue.forEach{
                f(it)
                if (it.input is EndRunner) { // FIXME: only end of one sub queue.
                    if (inQueue.isTerminated()) {
                        this.currentQueue++
                        if (this.isTerminated()) {
                            this.onEnd(it.input)
                        }
                    }
                }
            }
        }
    }

    fun isTerminated() : Boolean = this.currentQueue >= this.inputQueues.size
}


class RunnerItemChannel(val connector : Connector,
                        val data: JobConnectorData,
                        val link: JobLinkData?,
                        val nexts : List<LinkedRunnerItem>) {
    var inputQueues : InputQueues? = null

    private val identifier: UUID = UUID.randomUUID()

    val activePredecessors = HashSet<UUID>()

    val precedessors = mutableListOf<RunnerItemChannel>()

    suspend fun sendTo(from: JobLinkData?, element: Any?) {
        inputQueues?.send(from, InputItem(this.data, from, element))
    }

    fun initialize() {
        this.nexts.forEach {
            it.runner.declarePredecessor(this.identifier)
            it.runner.precedessors.add(this)
        }
    }

    fun postInitialize() {
        this.inputQueues = InputQueues(this, this::onEndRunner)
        GlobalScope.launch {
            this@RunnerItemChannel.consumeChannel()
        }
    }

    private fun onEndRunner(endElement: EndRunner) {
        this.connector.end()

        val terminate = EndRunner(this@RunnerItemChannel.identifier)
        GlobalScope.launch {
            this@RunnerItemChannel.nexts.forEach {
                it.link?.onEvent(EndEvent)
                it.runner.sendTo(this@RunnerItemChannel.link, terminate)
            }
        }
    }

    fun declarePredecessor(identifier: UUID) {
        this.activePredecessors.add(identifier)
    }

    fun isTerminated() : Boolean {
        return this@RunnerItemChannel.inputQueues?.isTerminated() ?: true
    }

    private suspend fun consumeChannel() {
        GlobalScope.launch {
            while (!isTerminated()) {

                this@RunnerItemChannel.inputQueues?.forEach {
                    element : InputItem ->
                    if (!(element.input is EndRunner)) {
                        connector.run(element) { branch: String, generatedElement: Any? ->
                            this@RunnerItemChannel.sendNext(generatedElement)
                        }
                    }
                }
                if (!isTerminated()) {
                    delay(30)
                }
            }
        }
    }

    private fun sendNext(generatedElement: Any?) {
        runBlocking {
            launch {
                nexts.forEach {
                    it.link?.onEvent(ItemEvent)
                    it.runner.sendTo(it.link?.data, generatedElement)
                }
            }
        }
    }
}

class RunableJob(val startNodes : List<LinkedRunnerItem>,
                 val allNodes: Collection<LinkedRunnerItem>) {
    fun execute() {
        runBlocking {
            val identifier = UUID.randomUUID()

            startNodes.map(LinkedRunnerItem::runner).forEach {
                it.declarePredecessor(identifier)
                GlobalScope.launch {
                    it.sendTo(null, null)
                    it.sendTo(null, EndRunner(identifier))
                }
            }
            while (allNodes.filter { !it.runner.isTerminated() }.isNotEmpty() ) {
                delay(300)
            }
        }
    }
}

class JobRunner() : Runner {
    override fun compile(job: Job): () -> Unit {
        val connectors : Map<UUID, Connector> = job.graph.nodes.entries.associate {
            val jobItem: JobConnector = it.value.data
            val f = jobItem.buildConnector()
            Pair(it.key, f)
        }

        val runners = HashMap<UUID, LinkedRunnerItem>()
        val allNodes: MutableSet<UUID> = job.graph.nodes.keys.toMutableSet()
        for (node in job.graph.startNodes()) {
            if (node is Node<JobConnector, JobLink>) {
                this.buildRunner(runners,
                    node,
                    null,
                    connectors,
                    allNodes)
            }
        }
        runners.values.map(LinkedRunnerItem::runner).forEach(RunnerItemChannel::initialize)
        runners.values.map(LinkedRunnerItem::runner).forEach(RunnerItemChannel::postInitialize)
        val startNode = job.graph.nodes
            .filter { it.value.precs.isEmpty() }
            .map { runners[ it.key ]!! }
            .toList()
        return RunableJob(startNode, runners.values)::execute
    }

    private fun buildRunner(runners : MutableMap<UUID, LinkedRunnerItem>,
                            node : Node<JobConnector, JobLink>,
                            linkTo: JobLink?,
                            connectors : Map<UUID, Connector>,
                            allNode : MutableSet<UUID>) {
        val nextRunners : List<LinkedRunnerItem> = node.nexts.map {
            val nextNode = it.end
            if (!runners.containsKey(nextNode.identifier)) {
                this.buildRunner(runners, nextNode, it.data, connectors, allNode)
            }
            runners[nextNode.identifier]
        }
            .filterNotNull()
            .toList();
        val item = RunnerItemChannel(connectors[node.identifier]!!,
            node.data.connectorData,
            linkTo?.data,
            nextRunners)
        val linkedRunner = LinkedRunnerItem(linkTo, item)
        allNode.remove(node.identifier)
        runners[node.identifier] = linkedRunner
    }
}
