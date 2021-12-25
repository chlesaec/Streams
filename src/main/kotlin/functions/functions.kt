package functions

/*import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
*/

interface FunctionConsumer {
    fun run(input : Any?, output : (Any?) -> Unit)
}
object DoNothingFunction : FunctionConsumer {
    override fun run(input : Any?, output : (Any?) -> Unit) {}
}

open class ChainedConsumers(val f1 : FunctionConsumer,
                            val f2 : FunctionConsumer)
    : FunctionConsumer {

    override fun run(input: Any?, consumer:  (Any?) -> Unit) {

        val csout : (Any?) -> Unit = {
            data: Any? -> //GlobalScope.launch {
                f2.run(data, consumer)
            //}
        }
        //GlobalScope.launch {
            f1.run(input, csout);
        //}
    }
}


fun parallelizeConsumers(fs : List<(Any?) -> Unit>) : (Any?) -> Unit {
    return { input : Any? ->  fs.forEach{ it(input) } }
}




