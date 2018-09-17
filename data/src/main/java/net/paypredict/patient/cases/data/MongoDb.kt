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
    fun orders(): MongoDatabase = mongoClient.getDatabase("orders")
    fun pokitDok(): MongoDatabase = mongoClient.getDatabase("pokitDok")
    fun nppes(): MongoDatabase = mongoClient.getDatabase("nppes")
    fun pipl(): MongoDatabase = mongoClient.getDatabase("pipl")
    fun ppPayers(): MongoDatabase = mongoClient.getDatabase("ppPayers")
    fun smartyStreets(): MongoDatabase = mongoClient.getDatabase("smartyStreets")

    object Collections {
        fun cases(): MongoCollection<Document> =
            orders().getCollection("cases")

        fun casesRaw(): MongoCollection<Document> =
            orders().getCollection("casesRaw")

        fun casesIssues(): MongoCollection<Document> =
            orders().getCollection("casesIssues")

        fun tradingPartners(): MongoCollection<Document> =
            pokitDok().getCollection("tradingPartners")

        fun eligibility(): MongoCollection<Document> =
            pokitDok().getCollection("eligibility")

        fun npiRegistry(): MongoCollection<Document> =
            nppes().getCollection("npiRegistry")

        object PPPayers {
            fun lookupPkd(): MongoCollection<Document> =
                ppPayers().getCollection("lookupPkd")

            fun zirmedPayers(): MongoCollection<Document> =
                ppPayers().getCollection("zirmedPayers")

            fun matchPayers(): MongoCollection<Document> =
                ppPayers().getCollection("matchPayers")

            fun usersMatchPayers(): MongoCollection<Document> =
                ppPayers().getCollection("usersMatchPayers")
        }
    }

    private val mongoClient: MongoClient by lazy {
        MongoClient(PatientCases.mongo.serverAddress)
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
