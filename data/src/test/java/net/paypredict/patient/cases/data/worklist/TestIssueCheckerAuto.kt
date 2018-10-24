package net.paypredict.patient.cases.data.worklist

import com.mongodb.client.MongoCollection
import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo._id
import net.paypredict.patient.cases.mongo.doc
import org.bson.Document

/**
 * Created by alexei.vylegzhanin@gmail.com on 10/23/2018.
 */
object TestIssueCheckerAuto {
    @JvmStatic
    fun main(args: Array<String>) {
        val casesRaw: MongoCollection<Document> = DBS.Collections.casesRaw()
        val casesIssues: MongoCollection<Document> = DBS.Collections.casesIssues()
        val case = casesRaw.find(doc { doc["case.Case.accessionNumber"] = args[0] }).first()
        casesIssues.deleteOne(case.getString("_id")!!._id())
        val issueCheckerAuto =
            IssueCheckerAuto(
                casesRaw = casesRaw,
                case = case
            )
        issueCheckerAuto.check()
    }
}