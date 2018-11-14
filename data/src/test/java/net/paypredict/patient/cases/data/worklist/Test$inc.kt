package net.paypredict.patient.cases.data.worklist

import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo._id
import net.paypredict.patient.cases.mongo.`$inc`
import net.paypredict.patient.cases.mongo.doc

fun main(args: Array<String>) {
    val json =
        DBS.Collections.settings()
            .findOneAndUpdate(
                "test_nextAccessionNumber"._id(),
                doc { self[`$inc`] = doc { self["value"] = 1 } },
                FindOneAndUpdateOptions()
                    .upsert(true)
                    .returnDocument(ReturnDocument.AFTER)
            )
            ?.toJson()

    println(json)
}