package net.paypredict.patient.cases.apis.smartystreets

import com.google.api.client.json.jackson2.JacksonFactory
import com.smartystreets.api.ClientBuilder
import com.smartystreets.api.us_street.Lookup
import com.smartystreets.api.us_street.MatchType
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.doc
import org.bson.Document
import java.io.File
import java.io.IOException
import java.util.*


/**
 *
 * Created by alexei.vylegzhanin@gmail.com on 8/30/2018.
 */
object SmartyStreetsApiTestAddress {
    enum class WarningsCondition {
        ANY, NONE, NOT_NONE;

        infix fun matches(warnings: String?): Boolean =
            when {
                this == ANY -> true
                this == NONE && warnings == "NONE" -> true
                this == NOT_NONE && warnings != "NONE" -> true
                else -> false
            }

        companion object {
            fun of(args: Array<String>): WarningsCondition =
                when (args.option("warnings")) {
                    "ANY" -> ANY
                    "NONE" -> NONE
                    else -> NOT_NONE
                }
        }
    }

    class LimitCondition(args: Array<String>) {
        private val limit: Int = args.option("limit")?.toInt() ?: 1
        private var count: Int = 0
        fun exceeded(): Boolean = ++count >= limit
    }

    private fun Array<String>.option(name: String): String? {
        val prefix = "--$name:"
        return firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val warningsCondition = WarningsCondition.of(args)
        val limitCondition = LimitCondition(args)

        val jacksonFactory = JacksonFactory()
        val client = ClientBuilder(smartyStreetsApiCredentials)
            .buildUsStreetApiClient()

        val collection = DBS.orders().getCollection("smartyStreetsTestAddress")
        val lines = File(args.last()).readLines()
        val headers = lines
            .first()
            .split('\t')
            .mapIndexed { index, line -> line to index }
            .toMap()

        operator fun List<String>.get(name: String): String? {
            val index = headers[name] ?: throw IOException("header `$name` not found")
            return this[index].let {
                when {
                    it.isNotBlank() -> it
                    else -> null
                }
            }
        }

        for (line in lines.drop(1)) {
            val record = line.split('\t')

            val warnings = record["warnings"]
            if (warningsCondition matches warnings) {
                val state = record["state"]
                val city = record["city"]
                val zip = record["zip"]
                val address1 = record["address1"]
                val address2 = record["address2"]


                val lookup = Lookup()
                lookup.match = MatchType.RANGE
                lookup.maxCandidates = 5

                lookup.street = address1 ?: continue
                lookup.street2 = address2
                lookup.city = city ?: continue
                lookup.state = state
                lookup.zipCode = zip


                print(line)
                val t0 = System.currentTimeMillis()
                client.send(lookup)

                var resultMode = lookup.match.name
                var resultDocs = lookup.result.map {
                    Document.parse(jacksonFactory.toString(it))
                }

                if (resultDocs.isEmpty()) {
                    lookup.match = MatchType.INVALID
                    client.send(lookup)
                    resultMode = lookup.match.name
                    resultDocs = lookup.result.map {
                        Document.parse(jacksonFactory.toString(it))
                    }
                }
                val t1 = System.currentTimeMillis()
                println()

                collection.insertOne(doc {
                    doc["line"] = line
                    doc["dateTime"] = Date()
                    doc["apiTimeMs"] = (t1 - t0).toInt()
                    doc["warnings"] = warnings
                    doc["lookup"] = Document.parse(jacksonFactory.toString(lookup))
                    doc["result"] = resultDocs
                    doc["resultMode"] = resultMode
                })

                if (limitCondition.exceeded()) break
            }
        }
    }
}