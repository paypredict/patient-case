package net.paypredict.patient.cases.data.worklist

import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo._id

fun main(args: Array<String>) {
    DBS.Collections
        .casesIssues()
        .find(args.last()._id())
        .firstOrNull()
        ?.toCaseIssue()
        ?.createOutXml()
}