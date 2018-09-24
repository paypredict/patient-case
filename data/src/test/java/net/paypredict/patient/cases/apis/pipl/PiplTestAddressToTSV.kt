package net.paypredict.patient.cases.apis.pipl

import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.mongo.opt
import java.io.File

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/31/2018.
 */
object PiplTestAddressToTSV {
    @JvmStatic
    fun main(args: Array<String>) {
        val names = listOf(
            "firstName", "lastName", "middleInitials", "gender", "dateOfBirth",
            "address1", "address2", "city", "zip", "state",
            "warnings")
        File(args[0]).bufferedWriter().use { out ->
            out.appendln(names.joinToString(separator = "\t"))
            DBS.orders()
                .getCollection("piplTestAddress")
                .find()
                .projection(doc {
                    doc["line"] = 1
                    doc["response.warnings"] = 1
                })
                .forEach { doc ->
                    out.append(doc.opt<String>("line")).append('\t')
                    val warnings = doc.opt<Any>("response", "warnings")
                    out.appendln(when(warnings) {
                        is List<*> -> warnings.joinToString().replace("\t", "| ")
                        else -> "NONE"
                    })
                }
        }
    }
}