package functions

import job.JobConnectorData
import job.JobLinkData

typealias OutputFunction = (String, Any?) -> Unit

class InputItem(
    val connectorOrigin : JobConnectorData,
    val linkOrigin: JobLinkData?,
    val input : Any?);

interface FunctionConsumer {
    fun run(input : InputItem, output : OutputFunction)
}
object DoNothingFunction : FunctionConsumer {
    override fun run(input : InputItem, output : OutputFunction) {}
}

fun parallelizeConsumers(fs : List<OutputFunction>) : OutputFunction {
    return { branch : String, input : Any? ->  fs.forEach{ it(branch, input) } }
}
