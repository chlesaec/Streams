package connectors.generators

import kotlinx.collections.immutable.toImmutableList
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class SourceFileGenerator(val packageName: String,
    val fileName: String) {

    private val classes = mutableListOf<ClassGenerator>()

    fun generate(targetFolder: File) : File {
        val f = File(targetFolder, this.fileName)
        if (f.exists()) {
            f.delete()
        }
        FileOutputStream(f).use {
            out: FileOutputStream ->
            out.write("package ${this.packageName}${System.lineSeparator()}${System.lineSeparator()}".toByteArray())
            this.classes.forEach { it.generateCode(out) }
        }
        return f
    }

    fun addClass(cg: ClassGenerator) {
        this.classes.add(cg)
    }

    fun classes() : List<ClassGenerator> {
        return this.classes.toImmutableList()
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

    fun fields() : Iterable<Parameter> {
        return this.fields.asIterable()
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
        methods.forEach {
            out.write("${System.lineSeparator()}".toByteArray())
            it.generateCode(out)
            out.write("${System.lineSeparator()}".toByteArray())
        }
        out.write("${System.lineSeparator()}\t}${System.lineSeparator()}".toByteArray());
    }

    fun addMethod(m: MethodGenerator) : CompanionBuilderGenerator {
        this.methods.add(m)
        return this
    }

}

class Parameter(val name: String, val type: String) {
    fun generateCode(out: OutputStream) {
        out.write("val ${this.name}: ${type}?".toByteArray())
    }

    fun generateForMethod(out: OutputStream) {
        out.write("${this.name}: ${this.type}".toByteArray());
    }
}

class MethodGenerator(val name: String) {
    private val lines = mutableListOf<String>()

    var returnType: String? = null

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

    fun addLine(l : String) {
        this.lines.add(l)
    }

    fun addInputParam(p : Parameter) : MethodGenerator {
        this.inputParams.add(p)
        return this
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
