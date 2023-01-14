package functions

import job.JobConnectorData

typealias OutputFunction = (String, Any?) -> Unit

class InputItem(
    val connectorOrigin : JobConnectorData,
    val input : Any?);

interface FunctionConsumer {
    fun run(input : InputItem, output : OutputFunction)
}
object DoNothingFunction : FunctionConsumer {
    override fun run(input : InputItem, output : OutputFunction) {}
}
/*
open class ChainedConsumers(val f1 : FunctionConsumer,
                            val f2 : FunctionConsumer)
    : FunctionConsumer {

    override fun run(input: InputItem, consumer:  OutputFunction) {

        val csout : (String, Any?) -> Unit = {
            branch: String, data: Any? -> //GlobalScope.launch {
                f2.run(data, consumer)
            //}
        }
        //GlobalScope.launch {
            f1.run(input, csout);
        //}
    }
}
*/

fun parallelizeConsumers(fs : List<OutputFunction>) : OutputFunction {
    return { branch : String, input : Any? ->  fs.forEach{ it(branch, input) } }
}
