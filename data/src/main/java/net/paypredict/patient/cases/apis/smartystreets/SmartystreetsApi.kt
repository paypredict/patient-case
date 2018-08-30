package net.paypredict.patient.cases.apis.smartystreets

import com.smartystreets.api.StaticCredentials
import net.paypredict.patient.cases.data.opt
import org.bson.Document
import java.io.File

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/30/2018.
 */

val smartyStreetsApiCredentials: StaticCredentials by lazy {
    StaticCredentials(FileConfiguration.authId, FileConfiguration.authToken)
}

private object FileConfiguration {
    private val conf: Document by lazy {
        val file = File("/PayPredict/conf/smartystreets.api.json")
        if (file.exists()) Document.parse(file.readText()) else Document()
    }

    val authId: String? by lazy { conf.opt<String>("authId") }
    val authToken: String? by lazy { conf.opt<String>("authToken") }
}