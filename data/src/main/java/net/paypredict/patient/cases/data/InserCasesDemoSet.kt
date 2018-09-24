package net.paypredict.patient.cases.data

import net.paypredict.patient.cases.mongo.DBS
import java.util.*

/**
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
fun main(args: Array<String>) {
    val cases = DBS.Collections.cases()
    cases.drop()
    for (i in 1..1000) {
        val uuid = UUID.randomUUID().toString()
        cases.insertOne(
            Case(
                req = uuid,
                ptnG = if (i % 2 == 0) "female" else "male",
                ptnBd = Date(),
                ptnLast = "Test $i",
                dxV = listOf("Q1")
            ).toDocument()
        )
    }
}