package net.paypredict.patient.cases.mongo

import com.mongodb.MongoClient
import com.mongodb.ServerAddress
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.UpdateOptions
import net.paypredict.patient.cases.PatientCases
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
        fun cases(): DocumentMongoCollection =
            orders().getCollection("cases")

        fun casesLog(): DocumentMongoCollection =
            orders().getCollection("casesLog")

        fun casesOut(): DocumentMongoCollection =
            orders().getCollection("casesOut")

        fun settings(): DocumentMongoCollection =
            orders().getCollection("settings")

        fun requisitionForms(): DocumentMongoCollection =
            orders().getCollection("requisitionForms")

        fun requisitionPDFs(): DocumentMongoCollection =
            orders().getCollection("requisitionPDFs")

        fun tradingPartners(): DocumentMongoCollection =
            pokitDok().getCollection("tradingPartners")

        fun eligibility(): DocumentMongoCollection =
            pokitDok().getCollection("eligibility")

        fun claimsConvert(): DocumentMongoCollection =
            pokitDok().getCollection("claimsConvert")

        fun npiRegistry(): DocumentMongoCollection =
            nppes().getCollection("npiRegistry")

        fun usStreet(): DocumentMongoCollection =
            smartyStreets().getCollection("usStreet")

        object PPPayers {
            @Suppress("FunctionName")
            fun find_zmPayerId(): DocumentMongoCollection =
                ppPayers().getCollection("find_zmPayerId")

            fun users_find_zmPayerId(): DocumentMongoCollection =
                ppPayers().getCollection("users_find_zmPayerId")

            fun zirmedPayers(): DocumentMongoCollection =
                ppPayers().getCollection("zirmedPayers")

            fun matchPayers(): DocumentMongoCollection =
                ppPayers().getCollection("matchPayers")

            fun usersMatchPayers(): DocumentMongoCollection =
                ppPayers().getCollection("usersMatchPayers")
        }
    }

    private val mongoClient: MongoClient by lazy {
        MongoClient(Conf.mongo.serverAddress)
    }
}

inline fun DocumentMongoCollection.upsertOne(
    filter: Bson,
    vararg fields: Pair<String, Any?>,
    onCreate: (filter: Bson) -> Unit = {}
) =
    upsertOne(filter, doc {
        self[`$set`] = doc {
            fields.forEach { self[it.first] = it.second }
        }
    }, onCreate)

inline fun DocumentMongoCollection.upsertOne(
    filter: Bson,
    update: Bson,
    onCreate: (filter: Bson) -> Unit = {}
) {
    updateOne(filter, update, UpdateOptions().upsert(true))
        ?.upsertedId
        ?.also {
            onCreate(filter)
        }
}

typealias DocumentMongoCollection = MongoCollection<Document>

fun DocumentMongoCollection.findById(id: String): Document? =
    find(id._id()).firstOrNull()


@Suppress("FunctionName")
fun String._id() = Document("_id", this)

fun Throwable.toDocument(): Document =
    doc {
        self["message"] = message
        self["class"] = this@toDocument.javaClass.name
        self["stackTrace"] = StringWriter()
            .also { str -> PrintWriter(str).use { printStackTrace(it) } }
            .toString()
    }

@Suppress("ObjectPropertyName", "unused")
const val `$`: String = "$"
@Suppress("ObjectPropertyName", "unused")
const val `$set`: String = "$" + "set"
@Suppress("ObjectPropertyName", "unused")
const val `$setOnInsert`: String = "$" + "setOnInsert"
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
const val `$gte`: String = "$" + "gte"
@Suppress("ObjectPropertyName", "unused")
const val `$lt`: String = "$" + "lt"
@Suppress("ObjectPropertyName", "unused")
const val `$lte`: String = "$" + "lte"
@Suppress("ObjectPropertyName", "unused")
const val `$exists`: String = "$" + "exists"
@Suppress("ObjectPropertyName", "unused")
const val `$in`: String = "$" + "in"
@Suppress("ObjectPropertyName", "unused")
const val `$or`: String = "$" + "or"
@Suppress("ObjectPropertyName", "unused")
const val `$and`: String = "$" + "and"
@Suppress("ObjectPropertyName", "unused")
const val `$push`: String = "$" + "push"
@Suppress("ObjectPropertyName", "unused")
const val `$inc`: String = "$" + "inc"

inline fun <reified T> Document.opt(vararg path: String): T? {
    var result: Any? = this
    for (key in path) {
        result = (result as? Document)?.get(key)
    }
    return result as? T
}

inline operator fun <reified T> Document?.invoke(vararg path: String): T? =
    this?.opt(*path)

@DslMarker
annotation class DslDocMarker

@DslDocMarker
class DocBuilder(val self: Document) {
    /** Set If Not Null */
    fun sinn(key: String, value: Any?) {
        if (value != null) self[key] = value
    }
}


inline fun doc(builder: DocBuilder.() -> Unit = {}): Document =
    DocBuilder(Document()).apply(builder).self


private object Conf {
    private val confDir: File by lazy {
        PatientCases.clientDir.resolve("conf")
    }

    private val conf: Document by lazy {
        val file1 = confDir.resolve("mongo.json")
        val file2 = confDir.resolve("claims-db.json")
        when {
            file1.isFile -> Document.parse(file1.readText())
            file2.isFile -> Document.parse(file2.readText())
            else -> Document()
        }
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
