package net.paypredict.patient.cases.pokitdok.claims.convert

import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo._id
import net.paypredict.patient.cases.mongo.upsertOne
import net.paypredict.patient.cases.pokitdok.client.ClaimsConvertQuery
import net.paypredict.patient.cases.pokitdok.client.digest
import net.paypredict.patient.cases.pokitdok.client.query
import org.bson.Document

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 12/20/2018.
 */
class ClaimsConvert(val isa837: String) {

    fun convert(): Document {
        val query = ClaimsConvertQuery(isa837)
        val digest = query.digest()
        val _id = digest._id()
        val collection = DBS.Collections.claimsConvert()
        return collection
            .find(_id).firstOrNull()
            ?: query
                .query { Document.parse(it.readText()) }
                .also { collection.upsertOne(_id, "src" to isa837, "data" to it) }
    }

}

