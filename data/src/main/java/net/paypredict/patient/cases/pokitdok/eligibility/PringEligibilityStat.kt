package net.paypredict.patient.cases.pokitdok.eligibility

import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.mongo.opt
import org.bson.Document
import java.util.concurrent.atomic.AtomicInteger

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 11/13/2018.
 */
object PringEligibilityStat {
    @JvmStatic
    fun main(args: Array<String>) {
        val reject_reason = mutableMapOf<String, AtomicInteger>()
        val follow_up_action = mutableMapOf<String, AtomicInteger>()
        for (doc in DBS.Collections.eligibility()
            .find()
            .projection(doc {
                self["data.reject_reason"] = 1
                self["data.follow_up_action"] = 1
            })) {
            val data: Document = doc.opt("data") ?: continue
            reject_reason.add("reject_reason", data)
            follow_up_action.add("follow_up_action", data)
        }

        reject_reason.print("******************** reject_reason ***********************")
        reject_reason.print("******************** follow_up_action ********************")
    }

    private fun MutableMap<String, AtomicInteger>.print(message: String) {
        println(message)
        println("name, count")
        for ((key, value) in entries.sortedByDescending { it.value.get() }) {
            println("$key, $value")
        }
    }

    private fun MutableMap<String, AtomicInteger>.add(name: String, data: Document) {
        val key: String = data.opt(name) ?: return
        getOrPut(key) { AtomicInteger(0) }.incrementAndGet()
    }
}

