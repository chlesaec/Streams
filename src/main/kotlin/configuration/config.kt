package configuration

sealed interface Stack<out T>
class End<out T> : Stack<T>
class StackElement<T>(val prec : Stack<T>, val element : T) : Stack<T>

open class Referential<T, R : Referential<T, R>>
internal constructor(val values : Map<String, T>,
                     val sub: Map<String, R>) {
    fun get(name : String) : T? {
        return this.values[name]
    }

    open fun sub(name : String) : R? {
        return this.sub[name]
    }

    fun fields() : Set<String> {
        return this.values.keys
    }

    fun subFields() : Set<String> = this.sub.keys

    fun explore(f : (Stack<String>, T) -> Unit) {
        this.exploreIn(End(), f)
    }

    private fun exploreIn(preNames : Stack<String>, f : (Stack<String>, T) -> Unit) {

        this.fields().forEach {
            val elem = this.get(it)
            if (elem != null) {
                f(StackElement<String>(preNames, it), elem)
            }
        }
        this.sub.entries.forEach {
            val stack = StackElement<String>(preNames, it.key)
            this.exploreIn(stack, f)
        }
    }

    fun all() : List<Pair<String, T>> =
        this.fields().map {
            Pair(it, this.get(it)!!)
        }

    open class Builder<T, R : Referential<T, R>, B : Builder<T, R, B>> {
        protected val properties = mutableMapOf<String, T>()

        protected val subs = mutableMapOf<String, R>()

        fun add(name: String, value: T) : B {
            this.properties.put(name, value)
            return this.self()
        }

        fun addSub(name: String, value : R) : B {
            this.subs.put(name, value)
            return this.self()
        }

        open fun self() : B {
            return this as B
        }

        open fun build() : Referential<T, R> {
            return Referential<T, R>(HashMap(this.properties),
                HashMap(this.subs))
        }
    }
}


class Config private constructor(values : Map<String, String>,
                     sub: Map<String, Config>)
       : Referential<String, Config>(values, sub) {

    class Builder : Referential.Builder<String, Config, Builder>() {
        override fun build(): Config {
            return Config(HashMap(this.properties),
                HashMap(this.subs))
        }

        override fun self() : Config.Builder {
            return this
        }
    }
}
