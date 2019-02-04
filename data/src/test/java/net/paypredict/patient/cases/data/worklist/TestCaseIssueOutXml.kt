package net.paypredict.patient.cases.data.worklist

import net.paypredict.patient.cases.PatientCases
import net.paypredict.patient.cases.mongo.DBS.Collections.cases
import net.paypredict.patient.cases.mongo._id

fun main(args: Array<String>) {
    PatientCases.client = "test"
    cases()
        .find(args.last()._id())
        .firstOrNull()
        ?.toCaseHist()
        ?.createOutXml()
}