package net.paypredict.patient.cases.apis.pipl

import com.pipl.api.search.SearchAPIRequest
import com.pipl.api.search.SearchConfiguration
import net.paypredict.patient.cases.mongo.opt
import org.bson.Document
import java.io.File


/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/28/2018.
 */


val piplApiSearchConfiguration: SearchConfiguration
    get() = SearchAPIRequest.getDefaultConfiguration().apply {
        apiKey = FileConfiguration.apiKey
        protocol = FileConfiguration.protocol
        host = FileConfiguration.host
        path = FileConfiguration.path
    }

private object FileConfiguration {
    private val def: SearchConfiguration = SearchConfiguration()
    private val conf: Document by lazy {
        val file = File("/PayPredict/conf/pipl.api.search.json")
        if (file.exists()) Document.parse(file.readText()) else Document()
    }

    val apiKey: String? by lazy { conf.opt<String>("apiKey") ?: def.apiKey }
    val protocol: String by lazy { conf.opt<String>("protocol") ?: def.protocol }
    val host: String by lazy { conf.opt<String>("host") ?: def.host }
    val path: String by lazy { conf.opt<String>("path") ?: def.path }
}