package connectors.generators

import java.io.File
import java.io.OutputStream

class FileGenerator() {

    fun generate(targetFolder: File) : File {
        //TODO
        return targetFolder
    }
}

class ClassGenerator(val name: String) {

    private val fields = mutableListOf<Parameter>()

    private val methods = mutableListOf<MethodGenerator>()

    var builder: CompanionBuilderGenerator? = null

    fun generateCode(out: OutputStream) {
        out.write("data class ${this.name}(".toByteArray())

        fields.forEachIndexed { index: Int, classField: Parameter ->
            if (index > 0) {
                out.write(",${System.lineSeparator()}\t".toByteArray())
            }
            classField.generateCode(out)
        }

        out.write(")${System.lineSeparator()}{".toByteArray())

        this.builder?.generate(out)

        methods.forEach {
            out.write("${System.lineSeparator()}".toByteArray())
            it.generateCode(out)
            out.write("${System.lineSeparator()}".toByteArray())
        }
        out.write("${System.lineSeparator()}}".toByteArray())
    }

    fun addField(f: Parameter) : ClassGenerator {
        this.fields.add(f)
        return this
    }

    fun addMethod(m: MethodGenerator) : ClassGenerator {
        this.methods.add(m)
        return this
    }
}

class CompanionBuilderGenerator() {

    private val methods = mutableListOf<MethodGenerator>()

    fun generate(out: OutputStream) {
        out.write("${System.lineSeparator()}\tcompanion object Builder {".toByteArray())
        out.write("${System.lineSeparator()}\t}${System.lineSeparator()}".toByteArray());
    }


}

class Parameter(val name: String, val type: String) {
    fun generateCode(out: OutputStream) {
        out.write("val ${this.name}: ${type}".toByteArray())
    }

    fun generateForMethod(out: OutputStream) {
        out.write("${this.name}: ${this.type}".toByteArray());
    }
}

class MethodGenerator(val name: String) {
    private val lines = mutableListOf<String>()

    val returnType: String? = null

    private val inputParams = mutableListOf<Parameter>()

    fun generateCode(out: OutputStream) {
        out.write("\tfun ${this.name}(".toByteArray())
        this.inputParams.forEachIndexed{
            index: Int, parameter: Parameter ->
            if (index > 0) {
                out.write(",${System.lineSeparator()}\t".toByteArray())
            }
            parameter.generateForMethod(out)
        }
        out.write(")".toByteArray())
        if (returnType is String && returnType != "Void") {
            out.write(": ${this.returnType}".toByteArray())
        }
        out.write("${System.lineSeparator()}\t {".toByteArray())
        lines.forEach {
            out.write("${System.lineSeparator()}\t\t${it}".toByteArray())
        }
        out.write("${System.lineSeparator()}\t}".toByteArray())
    }

    fun addLines(l : Array<String>) {
        l.forEach(this.lines::add)
    }
}

class BuilderGenerator(val newType : String,
    val inputType: String) {
    fun generateCode(out: OutputStream) {
        out.write("\tcompanion object Builder {".toByteArray())
        out.write("${System.lineSeparator()}\t".toByteArray())
        out.write("\tfun create(input: ${inputType}: ${this.newType}): ${newType} {".toByteArray())
        out.write("${System.lineSeparator()}\t\t}".toByteArray())
        out.write("${System.lineSeparator()}\t}".toByteArray())
    }
}
