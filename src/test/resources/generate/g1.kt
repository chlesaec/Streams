package connectors.db.generated;

data class C1(val s1 : String) {
    companion object Builder {
        fun create(): C1 = C1("Hello")
    }
}
