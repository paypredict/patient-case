package net.paypredict.patient.cases.utils

import com.mongodb.client.model.UpdateOptions
import net.paypredict.patient.cases.bson.`$set`
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.doc
import net.paypredict.patient.cases.data.opt
import net.paypredict.patient.cases.pokitdok.client.queryTradingPartners
import org.bson.Document

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/26/2018.
 */
object UpdateTradingPartners {
    @JvmStatic
    fun main(args: Array<String>) {
        val tradingPartnersAll: Document = queryTradingPartners { Document.parse(it.readText()) }
        val tradingPartners = DBS.Collections.tradingPartners()
        val meta = tradingPartnersAll.opt<Document>("meta")
        tradingPartnersAll.opt<List<*>>("data")
            ?.filterIsInstance<Document>()
            ?.forEach { data ->
                val id: String = data["id"] as String
                tradingPartners.updateOne(
                    doc { doc["_id"] = id },
                    doc {
                        doc[`$set`] = doc {
                            doc["meta"] = meta
                            doc["data"] = data
                        }
                    },
                    UpdateOptions().upsert(true)
                )
            }
    }
}