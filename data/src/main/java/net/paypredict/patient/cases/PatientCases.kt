package net.paypredict.patient.cases

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

    fun readConfDoc(): Document =
        Document.parse(conf.toJson())
}
