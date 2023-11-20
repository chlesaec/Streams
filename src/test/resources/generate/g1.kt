package connectors.db.generated;

data class Class1(val s1 : String) {
    companion object Builder {
        fun create(): Class1 = Class1("Hello")
    }
}
