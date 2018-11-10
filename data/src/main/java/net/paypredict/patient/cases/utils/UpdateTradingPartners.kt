package net.paypredict.patient.cases.utils

import com.mongodb.client.model.UpdateOptions
import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo.`$set`
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.mongo.opt
import net.paypredict.patient.cases.pokitdok.client.queryTradingPartners
import org.bson.Document

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/26/2018.
 */
object UpdateTradingPartners {
    @JvmStatic
    fun main(args: Array<String>) {
        val tradingPartnersAll: Document =
            queryTradingPartners { Document.parse(it.readText()) }
        val tradingPartners = DBS.Collections.tradingPartners()
        val meta = tradingPartnersAll.opt<Document>("meta")
        tradingPartnersAll.opt<List<*>>("data")
            ?.filterIsInstance<Document>()
            ?.forEach { data ->
                val id: String = data["id"] as String
                tradingPartners.updateOne(
                    doc { self["_id"] = id },
                    doc {
                        self[`$set`] = doc {
                            self["meta"] = meta
                            self["data"] = data
                        }
                    },
                    UpdateOptions().upsert(true)
                )
            }
    }
}