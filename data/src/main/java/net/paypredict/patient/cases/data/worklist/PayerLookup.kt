package net.paypredict.patient.cases.data.worklist

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.UpdateOptions
import net.paypredict.patient.cases.mongo.*
import net.paypredict.patient.cases.pokitdok.eligibility.PayersData
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
            .map {
                val _id = it["payerId"] as? String
                PayerId(
                    _id = _id,
                    payerName = zirmedPayers[_id]?.payerName,
                    checkable = true
                )
            }
            .firstOrNull()
            ?: systemCollection
                .find(filter)
                .map {
                    val _id = it["zmPayerId"] as? String
                    PayerId(
                        _id = _id,
                        payerName = zirmedPayers[_id]?.payerName,
                        checkable = it.opt<Int>("try") == 1
                    )
                }
                .firstOrNull()
    }

    operator fun set(payerName: String, payerId: String?) {
        usersCollection.updateOne(
            payerName.toPayerNameFilter(),
            doc {
                self[`$set`] = doc { self["payerId"] = payerId }
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

        private val zirmedPayers: Map<String, PayersData.ZirMedPayer> by lazy {
            PayersData().zirmedPayers
        }

    }
}

data class PayerId(
    val _id: String?,
    val payerName: String?,
    val checkable: Boolean
)