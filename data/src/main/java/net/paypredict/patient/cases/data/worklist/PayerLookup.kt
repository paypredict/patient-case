package net.paypredict.patient.cases.data.worklist

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.UpdateOptions
import net.paypredict.patient.cases.mongo.*
import org.bson.Document

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 10/15/2018.
 */
class PayerLookup {
    private val systemCollection: MongoCollection<Document> by lazy { DBS.Collections.PPPayers.find_zmPayerId() }
    private val usersCollection: MongoCollection<Document> by lazy { DBS.Collections.PPPayers.users_find_zmPayerId() }

    operator fun get(payerName: String): PayerId? {
        val filter = payerName.toPayerNameFilter()
        return usersCollection
            .find(filter)
            .map { PayerId(value = it["payerId"] as? String, checkable = true) }
            .firstOrNull()
            ?: systemCollection
                .find(filter)
                .map { PayerId(value = it["zmPayerId"] as? String, checkable = it.opt<Int>("try") == 1) }
                .firstOrNull()
    }

    operator fun set(payerName: String, payerId: String?) {
        usersCollection.updateOne(
            payerName.toPayerNameFilter(),
            doc {
                doc[`$set`] = doc { doc["payerId"] = payerId }
            },
            UpdateOptions().upsert(true)
        )
    }

    operator fun minusAssign(payerName: String) {
        usersCollection.deleteOne(payerName.toPayerNameFilter())
    }

    companion object {
        private fun String.toPayerNameFilter(): Document =
            toLowerCase()._id()
    }
}

data class PayerId(val value: String?, val checkable: Boolean)