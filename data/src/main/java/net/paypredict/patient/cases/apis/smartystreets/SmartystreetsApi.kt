package net.paypredict.patient.cases.apis.smartystreets

import com.google.api.client.json.jackson2.JacksonFactory
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.UpdateOptions
import com.smartystreets.api.ClientBuilder
import com.smartystreets.api.GoogleSerializer
import com.smartystreets.api.StaticCredentials
import com.smartystreets.api.exceptions.SmartyException
import com.smartystreets.api.us_street.*
import net.paypredict.patient.cases.VaadinBean
import net.paypredict.patient.cases.mongo.*
import net.paypredict.patient.cases.toDigest
import net.paypredict.patient.cases.toHexString
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.bson.Document
import java.io.File
import java.util.*
import javax.json.Json

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/30/2018.
 */

class UsStreet(private val autoRetryWithInvalid: Boolean = true) {
    private val client: Client = ClientBuilder(smartyStreetsApiCredentials).buildUsStreetApiClient()
    private val cacheOnly = Conf.cacheOnly
    private val serializer: GoogleSerializer by lazy { GoogleSerializer() }
    private val jacksonFactory: JacksonFactory by lazy { JacksonFactory() }
    private val collection: MongoCollection<Document> by lazy { DBS.Collections.usStreet() }

    fun send(lookup: Lookup) {
        val lookupJson = jacksonFactory.toString(lookup)
        val id =
            Json.createReader(lookupJson.reader()).readObject().toDigest().toHexString()
        val cache0 = collection.find(id._id()).firstOrNull()
        if (cache0 != null) {
            val result: List<Document> = cache0.opt<List<*>>("result")
                ?.filterIsInstance<Document>()
                ?: emptyList()

            lookup.result = arrayListOf(
                *serializer
                    .deserialize(
                        result.joinToString(prefix = "[", postfix = "]") { it.toJson() }.toByteArray(),
                        Array<Candidate>::class.java
                    )
                    ?: emptyArray()
            )
            return
        }
        if (cacheOnly) {
            lookup.result = arrayListOf()
            return
        }

        val meta = doc {
            doc["time"] = Date()
        }

        fun updateCache0() {
            collection.updateOne(id._id(), doc {
                doc[`$set`] = doc {
                    doc["meta"] = meta
                    doc["lookup"] = Document.parse(lookupJson)
                    doc["result"] = (lookup.result ?: emptyList<Candidate>()).map {
                        Document.parse(jacksonFactory.toString(it))
                    }
                }
            }, UpdateOptions().upsert(true))
        }

        try {
            try {
                client.send(lookup)
            } catch (e: SmartyException) {
                if (!autoRetryWithInvalid) throw e
            }
            if (autoRetryWithInvalid &&
                lookup.match != MatchType.INVALID &&
                lookup.result.isEmpty()
            ) {
                lookup.match = MatchType.INVALID
                meta["retryWithInvalid"] = true
                send(lookup)
            }
            updateCache0()
        } catch (e: SmartyException) {
            meta["error"] = e.toDocument()
            updateCache0()
            throw e
        }
    }
}


private val smartyStreetsApiCredentials: StaticCredentials by lazy {
    StaticCredentials(Conf.authId, Conf.authToken)
}

@VaadinBean
class FootNote(
    val name: String,
    val label: String,
    val note: String,
    val level: Level = Level.WARNING
) {

    enum class Level : Comparable<Level> {
        INFO,
        WARNING,
        ERROR
    }

    companion object {
        @Suppress("unused")
        fun encodeFootNoteSet(footNoteSet: FootNoteSet): String? =
            footNoteSet.joinToString(separator = "") { it.name + "#" }

        fun decodeFootNoteSet(string: String?): FootNoteSet =
            string
                ?.splitToSequence('#')
                ?.mapNotNull {
                    try {
                        footNoteMap[it] ?: if (it.isNotBlank())
                            FootNote(it, "Unknown footnote $it#", "Unknown footnote $it#", Level.ERROR) else
                            null
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
                ?.toSet()
                ?: emptySet()
    }
}

val footNoteMap: Map<String, FootNote> by lazy {
    mutableMapOf<String, FootNote>()
        .also { map ->
            CSVParser.parse(
                Conf::class.java.getResourceAsStream("dic_footnotes.csv")
                    .reader().use { it.readText().removePrefix("\uFEFF").reader() },
                CSVFormat.EXCEL.withHeader()
            ).use { parser ->
                for (record in parser) {
                    val name = record["Value"] ?: continue
                    val label = record["Definition"] ?: continue
                    val note = record["Details"] ?: continue
                    val level = record["Level"]?.toUpperCase() ?: FootNote.Level.INFO.name
                    map[name] = FootNote(
                        name = name,
                        label = label,
                        note = note,
                        level = try {
                            FootNote.Level.valueOf(level)
                        } catch (e: Exception) {
                            FootNote.Level.INFO
                        }
                    )
                }
            }
        }
}

typealias FootNoteSet = Set<FootNote>

val Analysis.footNoteSet: FootNoteSet
    get() = FootNote.decodeFootNoteSet(footnotes)

private object Conf {
    private val conf: Document by lazy {
        val file = File("/PayPredict/conf/smartystreets.api.json")
        if (file.exists()) Document.parse(file.readText()) else Document()
    }

    val authId: String? by lazy { conf.opt<String>("authId") }
    val authToken: String? by lazy { conf.opt<String>("authToken") }

    val cacheOnly: Boolean by lazy { conf.opt<Boolean>("cacheOnly") ?: false }
}