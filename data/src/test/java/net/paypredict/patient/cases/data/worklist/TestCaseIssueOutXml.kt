package net.paypredict.patient.cases.data.worklist

import net.paypredict.patient.cases.mongo.DBS.Collections.cases
import net.paypredict.patient.cases.mongo._id

fun main(args: Array<String>) {
    cases()
        .find(args.last()._id())
        .firstOrNull()
        ?.toCaseHist()
        ?.createOutXml()
}