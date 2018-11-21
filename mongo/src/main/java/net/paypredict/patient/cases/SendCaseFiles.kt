package net.paypredict.patient.cases

import net.paypredict.patient.cases.mongo.opt
import java.io.BufferedWriter
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 11/21/2018.
 */
object SendCaseFiles {

    @JvmStatic
    fun main(args: Array<String>) {
        val srcDir = File(args.findOption("src"))
        val outDir = File(args.findOption("out"))
        srcDir.resolve(
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")) + ".csv"
        ).bufferedWriter().use { csv ->
            csv.append("file,accessionNumber,guid,firstName,lastName")
            csv.newLine()
            for (file in srcDir.listFiles() ?: emptyArray()) {
                if (!file.isFile) continue
                val case = try {
                    Import.readXmlCaseAsDocument(file)
                        .also { file.copyTo(outDir.resolve(file.name), overwrite = true) }
                } catch (e: Throwable) {
                    continue
                }
                val accessionNumber: String? = case.opt("Case", "accessionNumber")
                val caseGUID: String? = case.opt("Case", "caseGUID")
                val patientFirstName: String? = case.opt("Case", "Patient", "firstName")
                val patientLastName: String? = case.opt("Case", "Patient", "lastName")
                csv.writeln(file.name, accessionNumber, caseGUID, patientFirstName, patientLastName)
            }
        }
    }

    private fun BufferedWriter.writeln(vararg values: String?) {
        append(values.joinToString { it.qt })
        newLine()
    }

    private val String?.qt: String
        get() =
            when {
                this == null -> ""
                ',' !in this -> this
                else -> "\"$this\""
            }

    private fun Array<String>.findOption(name: String): String {
        val prefix = "--$name:"
        return find { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?: throw Exception("parameter $prefix required")
    }

}

