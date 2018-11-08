package net.paypredict.patient.cases.data

import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.mongo.opt
import org.bson.Document

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 10/3/2018.
 */
object BuildCasesSubscriberCollection {
    @JvmStatic
    fun main(args: Array<String>) {
        val subscriberArraysList = DBS.Collections.cases().find()
            .projection(doc { doc["case.Case.SubscriberDetails.Subscriber"] = 1 })
            .toList()

        val casesSubscriber = DBS.orders()
            .getCollection("casesSubscriber")
            .apply { drop() }

        subscriberArraysList
            .flatMap { caseDoc ->
                val id = caseDoc["_id"]
                caseDoc.opt<List<*>>("case", "Case", "SubscriberDetails", "Subscriber")
                    ?.asSequence()
                    ?.filterIsInstance<Document>()
                    ?.map { it.apply { this["id"] = id } }
                    ?.toList()
                    ?: emptyList()
            }
            .forEach {
                casesSubscriber.insertOne(it)
            }
    }
}