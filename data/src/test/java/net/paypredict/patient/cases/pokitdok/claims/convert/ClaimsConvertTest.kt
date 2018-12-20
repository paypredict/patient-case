package net.paypredict.patient.cases.pokitdok.claims.convert

import net.paypredict.patient.cases.PatientCases.client
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import java.io.File

fun main(args: Array<String>) {
    client = args[1]
    val isa837 = File(args[0]).readText()
    val convert = ClaimsConvert(isa837)
    val result = convert.convert()
    println(result.toJson(JsonWriterSettings(JsonMode.SHELL, true)))
}