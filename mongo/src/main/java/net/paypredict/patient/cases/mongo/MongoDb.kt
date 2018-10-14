package net.paypredict.patient.cases.mongo

import com.mongodb.MongoClient
import com.mongodb.ServerAddress
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.UpdateOptions
import org.bson.Document
import org.bson.conversions.Bson
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/12/2018.
 */
object DBS {
    fun orders(): MongoDatabase = mongoClient.getDatabase("orders")
    fun pokitDok(): MongoDatabase = mongoClient.getDatabase("pokitDok")
    fun nppes(): MongoDatabase = mongoClient.getDatabase("nppes")
    fun pipl(): MongoDatabase = mongoClient.getDatabase("pipl")
    fun ppPayers(): MongoDatabase = mongoClient.getDatabase("ppPayers")
    fun smartyStreets(): MongoDatabase = mongoClient.getDatabase("smartyStreets")
    fun ptn(): MongoDatabase = mongoClient.getDatabase("ptn")

    object Collections {
        fun cases(): MongoCollection<Document> =
            orders().getCollection("cases")

        fun casesRaw(): MongoCollection<Document> =
            orders().getCollection("casesRaw")

        fun casesIssues(): MongoCollection<Document> =
            orders().getCollection("casesIssues")

        fun requisitionForms(): MongoCollection<Document> =
            orders().getCollection("requisitionForms")

        fun requisitionPDFs(): MongoCollection<Document> =
            orders().getCollection("requisitionPDFs")

        fun tradingPartners(): MongoCollection<Document> =
            pokitDok().getCollection("tradingPartners")

        fun eligibility(): MongoCollection<Document> =
            pokitDok().getCollection("eligibility")

        fun npiRegistry(): MongoCollection<Document> =
            nppes().getCollection("npiRegistry")

        fun usStreet(): MongoCollection<Document> =
            smartyStreets().getCollection("usStreet")

        object PPPayers {
            @Suppress("FunctionName")
            fun find_zmPayerId(): MongoCollection<Document> =
                ppPayers().getCollection("find_zmPayerId")

            fun zirmedPayers(): MongoCollection<Document> =
                ppPayers().getCollection("zirmedPayers")

            fun matchPayers(): MongoCollection<Document> =
                ppPayers().getCollection("matchPayers")

            fun usersMatchPayers(): MongoCollection<Document> =
                ppPayers().getCollection("usersMatchPayers")
        }
    }

    private val mongoClient: MongoClient by lazy {
        MongoClient(Conf.mongo.serverAddress)
    }
}

fun MongoCollection<Document>.upsertOne(filter: Bson, vararg fields: Pair<String, Any?>) =
    upsertOne(filter, doc {
        doc[`$set`] = doc {
            fields.forEach { doc[it.first] = it.second }
        }
    })

fun MongoCollection<Document>.upsertOne(filter: Bson, update: Bson) {
    updateOne(filter, update, UpdateOptions().upsert(true))
}

fun MongoCollection<Document>.findById(id: String): Document? =
    find(id._id()).firstOrNull()


@Suppress("FunctionName")
fun String._id() = Document("_id", this)

fun Throwable.toDocument(): Document =
    doc {
        doc["message"] = message
        doc["class"] = this@toDocument.javaClass.name
        doc["stackTrace"] = StringWriter()
            .also { str -> PrintWriter(str).use { printStackTrace(it) } }
            .toString()
    }

@Suppress("ObjectPropertyName", "unused")
const val `$`: String = "$"
@Suppress("ObjectPropertyName", "unused")
const val `$set`: String = "$" + "set"
@Suppress("ObjectPropertyName", "unused")
const val `$text`: String = "$" + "text"
@Suppress("ObjectPropertyName", "unused")
const val `$search`: String = "$" + "search"
@Suppress("ObjectPropertyName", "unused")
const val `$meta`: String = "$" + "meta"
@Suppress("ObjectPropertyName", "unused")
const val `$ne`: String = "$" + "ne"
@Suppress("ObjectPropertyName", "unused")
const val `$gt`: String = "$" + "gt"
@Suppress("ObjectPropertyName", "unused")
const val `$exists`: String = "$" + "exists"
@Suppress("ObjectPropertyName", "unused")
const val `$or`: String = "$" + "or"
@Suppress("ObjectPropertyName", "unused")
const val `$and`: String = "$" + "and"

inline fun <reified T> Document.opt(vararg path: String): T? {
    var result: Any? = this
    for (key in path) {
        result = (result as? Document)?.get(key)
    }
    return result as? T
}

inline operator fun <reified T> Document?.invoke(vararg path: String): T? =
    this?.opt(*path)

class DocBuilder(val doc: Document) {
    fun opt(key: String, value: Any?) {
        if (value != null) doc[key] = value
    }
}

fun doc(builder: DocBuilder.() -> Unit = {}): Document =
    DocBuilder(Document()).apply(builder).doc


private object Conf {
    val dir = File("/PayPredict/ptn")
    private val confDir = dir.resolve("conf")
    private val confFile = confDir.resolve("ptn.json")
    private val conf: Document by lazy {
        if (confFile.isFile)
            Document.parse(confFile.readText()) else
            Document()
    }

    object mongo {
        val host: String by lazy { conf.opt<String>("mongo", "host") ?: ServerAddress.defaultHost() }
        val port: Int by lazy { (conf.opt<Number>("mongo", "port") ?: ServerAddress.defaultPort()).toInt() }

        val serverAddress: ServerAddress by lazy {
            ServerAddress(
                host,
                port
            )
        }
    }
}
