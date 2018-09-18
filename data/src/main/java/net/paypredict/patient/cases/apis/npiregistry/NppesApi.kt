package net.paypredict.patient.cases.apis.npiregistry

import com.mongodb.client.model.UpdateOptions
import net.paypredict.patient.cases.bson.`$set`
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.doc
import net.paypredict.patient.cases.data.opt
import org.bson.Document
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 *
 * * https://npiregistry.cms.hhs.gov/registry/help-api
 * * https://npiregistry.cms.hhs.gov/api/resultsDemo2
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/17/2018.
 */
object NpiRegistry {
    fun find(npi: String): Document {
        Cache.get(npi)?.let { return it }

        if (!Conf.enabled)
            throw NpiRegistryException("NpiRegistry API isn't enabled")
        val connection: HttpURLConnection =
            URL("${Conf.npiRegistryUrl}?number=$npi").openConnection() as HttpURLConnection
        connection.connect()
        if (connection.responseCode != 200)
            throw NpiRegistryException("invalid connection.responseCode: " + connection.responseCode)

        val text = connection.inputStream.reader().readText()
        val response = Document.parse(text)
        DBS.Collections.npiRegistry().updateOne(doc { doc["_id"] = npi }, doc {
            doc[`$set`] = doc {
                doc["response"] = response
                doc["updated"] = Date()
            }
        }, UpdateOptions().upsert(true))

        Cache.put(npi, response)

        val errors = response.opt<List<*>>("Errors")
        if (errors != null) {
            val errorsList = errors
                .asSequence()
                .filterIsInstance<Document>()
                .map { it.opt<String>("description") ?: it.toJson() }
                .toList()
            throw NpiRegistryException("API returns errors: " + errorsList.joinToString(), errorsList)
        }
        return response
    }

    private object Cache {
        fun get(npi: String): Document? = lock.withLock {
            data[npi]?.data?.let {
                Document.parse(it)
            }
        }

        fun put(npi: String, document: Document): Unit = lock.withLock {
            val time = System.currentTimeMillis()
            data[npi] = Entry(npi, document.toJson(), time)
            removeOldData(time)
        }

        private fun removeOldData(time: Long) {
            val limit = time - 1000L * 60 * 60
            for (entry in data.values.filter { it.time < limit }) {
                data.remove(entry.npi)
            }
        }

        private val data = mutableMapOf<String, Entry>()
        private val lock = ReentrantLock()

        private data class Entry(val npi: String, val data: String, val time: Long)
    }

}

class NpiRegistryException(override val message: String, val errors: List<String> = emptyList()) : Exception()

private object Conf {
    private val conf: Document by lazy {
        val file = File("/PayPredict/conf/nppes.api.json")
        if (file.exists()) Document.parse(file.readText()) else Document()
    }

    val enabled: Boolean by lazy { conf.opt<Boolean>("enabled") ?: true }

    val npiRegistryUrl: String by lazy {
        conf.opt<String>("npiRegistryUrl")
            ?: "https://npiregistry.cms.hhs.gov/api/resultsDemo2/"
    }
}