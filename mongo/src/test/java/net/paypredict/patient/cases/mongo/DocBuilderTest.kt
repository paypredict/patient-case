package net.paypredict.patient.cases.mongo

import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings

object DocBuilderTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val doc = doc {
            self["a"] = doc {
                self["b"] = ""
                sinn("c", null)
                sinn("d", 1)
            }
        }

        println(doc.toJson(JsonWriterSettings(JsonMode.SHELL, true)))
    }
}