package net.paypredict.patient.cases.data.worklist

import com.mongodb.client.MongoCollection
import net.paypredict.patient.cases.mongo.DBS.Collections.cases
import net.paypredict.patient.cases.mongo._id
import net.paypredict.patient.cases.mongo.`$set`
import net.paypredict.patient.cases.mongo.doc
import org.bson.Document

/**
 * Created by alexei.vylegzhanin@gmail.com on 10/23/2018.
 */
object TestIssueCheckerAuto {
    @JvmStatic
    fun main(args: Array<String>) {
        val cases: MongoCollection<Document> = cases()
        val case = cases.find(doc { doc["case.Case.accessionNumber"] = args[0] }).first()

        val _id = case.getString("_id")!!._id()
        cases.updateOne(_id, doc {
            doc[`$set`] = doc {
                doc["status.problems"] = 0
                doc["status.value"] = null
            }
        })
        cases.deleteOne(_id)

        val issueCheckerAuto =
            IssueCheckerAuto(
                cases = cases,
                case = case
            )
        issueCheckerAuto.check()
    }
}