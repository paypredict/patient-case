package net.paypredict.patient.cases

import com.mongodb.ServerAddress
import net.paypredict.patient.cases.data.opt
import org.bson.Document
import java.io.File

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
object PatientCases {
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
        val port: Int by lazy { (conf.opt<Number>("mongo", "port") ?:  ServerAddress.defaultPort()).toInt() }

        val serverAddress: ServerAddress by lazy {
            ServerAddress(host, port)
        }
    }
}
