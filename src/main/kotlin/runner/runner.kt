package runner

import functions.FunctionConsumer
import graph.Node
import javafx.application.Application.launch
import job.Job
import job.JobConnector
import job.JobLink
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

class RunnerItemChannel(val function : FunctionConsumer,
                        val nexts : List<RunnerItemChannel>) {
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
        this.nexts.forEach { it.declarePrecedent(this.identifier) }
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
                                    it.execute(targetElement)
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
                    this@RunnerItemChannel.nexts.forEach { it.execute(terminate) }
                }
            }
        }
    }
}

class RunableJob(val startNodes : List<RunnerItemChannel>) {
    fun execute() {
        runBlocking {
            val identifier = UUID.randomUUID()
            startNodes.forEach {
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

        val runners = HashMap<UUID, RunnerItemChannel>()
        val allNodes: MutableSet<UUID> = job.graph.nodes.keys.toMutableSet()
        while (allNodes.isNotEmpty()) {
            val next: UUID = allNodes.elementAt(0)
            val node: Node<JobConnector, JobLink>? = job.graph.nodes[next]
            if (node is Node<JobConnector, JobLink>) {
                this.buildRunner(runners,
                    node,
                    connectors,
                    allNodes)
            }
        }
        runners.values.forEach(RunnerItemChannel::initialize)
        val startNode = job.graph.nodes
            .filter { it.value.precs.isEmpty() }
            .map { runners[ it.key ]!! }
            .toList()
        return RunableJob(startNode)::execute
    }

    private fun buildRunner(runners : MutableMap<UUID, RunnerItemChannel>,
                            node : Node<JobConnector, JobLink>,
                            connectors : Map<UUID, FunctionConsumer>,
                            allNode : MutableSet<UUID>) {
        val nextRunners : List<RunnerItemChannel> = node.nexts.map {
            val nextNode = it.end
            if (!runners.containsKey(nextNode.identifier)) {
                this.buildRunner(runners, nextNode, connectors, allNode)
            }
            runners[nextNode.identifier]
        }
            .filterNotNull()
            .toList();
        val item = RunnerItemChannel(connectors[node.identifier]!!, nextRunners)
        allNode.remove(node.identifier)
        runners[node.identifier] = item
    }
}