package net.paypredict.patient.cases.data

import com.mongodb.MongoClient
import com.mongodb.ServerAddress
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import net.paypredict.patient.cases.PatientCases
import org.bson.Document
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContextListener
import javax.servlet.annotation.WebListener
import kotlin.concurrent.withLock

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/12/2018.
 */
object DBS {
    fun ptn(): MongoDatabase = mongoClient.getDatabase(databaseName)
    fun pokitDok(): MongoDatabase = mongoClient.getDatabase("pokitDok")

    object Collections {
        fun cases(): MongoCollection<Document> =
            ptn().getCollection("cases")

        fun casesRaw(): MongoCollection<Document> =
            ptn().getCollection("casesRaw")
        fun casesIssues(): MongoCollection<Document> =
            ptn().getCollection("casesIssues")

        fun tradingPartners(): MongoCollection<Document> =
            pokitDok().getCollection("tradingPartners")
        fun eligibility(): MongoCollection<Document> =
            pokitDok().getCollection("eligibility")
    }

    private val mongoConf : Document by lazy {
        (PatientCases.readConfDoc()["mongo"] as? Document) ?: Document()
    }


    private val address: ServerAddress by lazy {
        ServerAddress(
            mongoConf["host"] as? String ?: ServerAddress.defaultHost(),
            (mongoConf["port"] as? Number ?: ServerAddress.defaultPort()).toInt()
        )
    }

    private val mongoClient: MongoClient by lazy {
        MongoClient(address)
    }

    private val databaseName: String  by lazy {
        mongoConf["db"] as? String ?: "ptn"
    }

    fun addShutdownListener(shutdownListener: () -> Unit) = lock.withLock {
        shutdownListeners += shutdownListener
    }
}

private val lock = ReentrantLock()
private val shutdownListeners = mutableListOf<() -> Unit>()

@WebListener
class PatientIdContextListener : ServletContextListener {
    override fun contextInitialized(sce: ServletContextEvent) {
    }

    override fun contextDestroyed(sce: ServletContextEvent) {
        lock.withLock { shutdownListeners.toList() }.forEach { listener ->
            try {
                listener()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
