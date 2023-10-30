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

        protected val subs = mutableMapOf<String, B>()

        fun forProperties(f: (String, T) -> Unit) {
            this.properties.forEach(f)
        }

        fun forSubreferentials(f : (String, B) -> Unit) {
            this.subs.forEach(f)
        }

        fun add(name: String, value: T) : B {
            this.properties[name] = value
            return this.self()
        }

        fun get(name: String) : T? {
            return this.properties[name]
        }

        fun addSub(name: String, value : B) : B {
            this.subs.put(name, value)
            return value
        }

        fun getSub(name: String) : B? {
            return this.subs[name]
        }

        open fun self() : B {
            return this as B
        }

        open fun build() : R {
            val subRef : Map<String, R> = this.subs.entries
                .associate { it.key to it.value.build() }

            val ref = Referential(HashMap(this.properties), subRef)
            return ref as R
        }
    }

    open fun <B : Builder<T, R, B>> toBuilder() : B {
        val builder = Referential.Builder<T, R, B>() as B
        this.toBuilder(builder)
        return builder
    }

    protected fun <B : Builder<T, R, B>> toBuilder(builder : B) {
        this.values.forEach {
            builder.add(it.key, it.value)
        }
        this.sub.forEach {
            val builder = it.value.toBuilder<B>()
            builder.addSub(it.key, builder)
        }
    }
}


class Config private constructor(values : Map<String, String>,
                     sub: Map<String, Config>)
       : Referential<String, Config>(values, sub) {

    class Builder : Referential.Builder<String, Config, Builder>() {
        override fun build(): Config {
            val subRef : Map<String, Config> = this.subs.entries
                .associate { it.key to it.value.build() }

            return Config(HashMap(this.properties), subRef)
        }

        override fun self() : Builder {
            return this
        }
    }

    override fun <B : Referential.Builder<String, Config, B>> toBuilder(): B {
        val builder = Builder()
        this.toBuilder(builder)
        return builder as B
    }
}
