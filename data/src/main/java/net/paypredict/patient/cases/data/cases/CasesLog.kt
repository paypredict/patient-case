package net.paypredict.patient.cases.data.cases

import net.paypredict.patient.cases.data.worklist.CaseAttr
import net.paypredict.patient.cases.data.worklist.CaseHist
import net.paypredict.patient.cases.data.worklist.CaseStatus
import net.paypredict.patient.cases.data.worklist.toDocument
import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo.DocumentMongoCollection
import net.paypredict.patient.cases.mongo.doc
import org.bson.Document
import java.util.*

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 11/10/2018.
 */
data class CasesLog(
    val id: String,
    val level: LogLevel = LogLevel.INFO,
    val accession: String? = null,
    val time: Date = Date(),
    val source: String? = null,
    val action: String? = null,
    val message: String? = null,
    val user: String? = null,
    val status: CaseStatus? = null,
    val ext: Document? = null
)

enum class LogLevel {
    INFO, WARNING, ERROR
}

fun CaseAttr.toCasesLog(
    id: String = this._id,
    level: LogLevel = LogLevel.INFO,
    accession: String? = this.accession,
    source: String? = null,
    action: String? = null,
    message: String? = null,
    user: String? = null,
    status: CaseStatus? = null,
    ext: Document? = null
) =
    CasesLog(
        id = id,
        level = level,
        accession = accession,
        source = source,
        action = action,
        message = message,
        user = user,
        status = status,
        ext = ext
    )

fun CaseHist.toCasesLog(
    id: String = this._id,
    level: LogLevel = LogLevel.INFO,
    accession: String? = this.accession,
    source: String? = null,
    action: String? = null,
    message: String? = null,
    user: String? = null,
    status: CaseStatus? = null,
    ext: Document? = null
) =
    CasesLog(
        id = id,
        level = level,
        source = source,
        action = action,
        accession = accession,
        message = message,
        user = user,
        status = status,
        ext = ext
    )

fun CasesLog.insert(casesLog: DocumentMongoCollection = DBS.Collections.casesLog()) {
    casesLog.insertOne(toDocument())
}

fun CasesLog.toDocument(): Document =
    doc {
        self["id"] = id
        sinn("accession", accession)
        self["time"] = time
        self["level"] = level.name
        sinn("source", source)
        sinn("action", action)
        sinn("message", message)
        sinn("user", user)
        sinn("status", status?.toDocument())
        sinn("ext", ext)
    }
