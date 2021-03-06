package runner

import functions.FunctionConsumer
import graph.Node
import javafx.application.Application.launch
import job.*
import job.Job
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

interface Runner {
    fun compile(job: Job) : () -> Unit
}

class RunnerItem(val function : FunctionConsumer,
                 val nexts : List<RunnerItem>) {

    fun execute(element: Any?) {
        function.run(element) {
                targetElement : Any? ->
                nexts.forEach {
                    it.execute(targetElement)
                }
        }
    }
}

class EndRunner(val identifier : UUID)

interface EventHandler {
    fun elementReceived(element: Any?)
}

class LinkedRunnerItem(
    val link: JobLink?,
    val runner : RunnerItemChannel)

class RunnerItemChannel(val function : FunctionConsumer,
                        val nexts : List<LinkedRunnerItem>) {
    val queue = Channel<Any?> {}

    val identifier = UUID.randomUUID()

    val precedents = HashSet<UUID>()

    var started = false

    suspend fun execute(element: Any?) {
        if (!started) {
            started = true
            this.consumeChannel()
        }
        queue.send(element)
    }

    fun initialize() {
        this.nexts.forEach { it.runner.declarePrecedent(this.identifier) }
    }

    fun declarePrecedent(identifier : UUID) {
        this.precedents.add(identifier)
    }

    private suspend fun consumeChannel() {
        GlobalScope.launch {
            while (this@RunnerItemChannel.precedents.isNotEmpty()) {
                for (element in this@RunnerItemChannel.queue) {
                    if (element is EndRunner) {
                        this@RunnerItemChannel.precedents.remove(element.identifier)
                    }
                    else {
                        function.run(element) { targetElement: Any? ->
                            GlobalScope.launch {
                                nexts.forEach {
                                    it.link?.onEvent(ItemEvent)
                                    it.runner.execute(targetElement)
                                }
                            }
                        }
                    }
                }
                if (this@RunnerItemChannel.precedents.isNotEmpty()) {
                    delay(30)
                }
                else {
                    val terminate = EndRunner(this@RunnerItemChannel.identifier)
                    this@RunnerItemChannel.nexts.forEach {
                        it.link?.onEvent(EndEvent)
                        it.runner.execute(terminate)
                    }
                }
            }
        }
    }
}

class RunableJob(val startNodes : List<LinkedRunnerItem>) {
    fun execute() {
        runBlocking {
            val identifier = UUID.randomUUID()
            startNodes.map(LinkedRunnerItem::runner).forEach {
                it.declarePrecedent(identifier)
                it.execute(null)
                it.execute(EndRunner(identifier))
            }
        }
    }
}

class JobRunner() : Runner {
    override fun compile(job: Job): () -> Unit {
        val connectors : Map<UUID, FunctionConsumer> = job.graph.nodes.entries.associate {
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
        val startNode = job.graph.nodes
            .filter { it.value.precs.isEmpty() }
            .map { runners[ it.key ]!! }
            .toList()
        return RunableJob(startNode)::execute
    }

    private fun buildRunner(runners : MutableMap<UUID, LinkedRunnerItem>,
                            node : Node<JobConnector, JobLink>,
                            linkTo: JobLink?,
                            connectors : Map<UUID, FunctionConsumer>,
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
        val item = RunnerItemChannel(connectors[node.identifier]!!, nextRunners)
        val linkedRunner = LinkedRunnerItem(linkTo, item)
        allNode.remove(node.identifier)
        runners[node.identifier] = linkedRunner
    }
}
