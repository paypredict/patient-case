package net.paypredict.patient.cases.auth0

import com.auth0.AuthenticationController
import net.paypredict.patient.cases.mongo.opt
import org.bson.Document
import java.io.File
import javax.servlet.http.HttpServletRequest

object Auth0 {
    fun buildAuthenticationController(): AuthenticationController =
        AuthenticationController
            .newBuilder(domain, clientId, clientSecret)
            .build()

    val domain: String? by lazy { conf.opt<String>("auth0", "domain") }
    private val clientId: String? by lazy { conf.opt<String>("auth0", "clientId") }
    private val clientSecret: String? by lazy { conf.opt<String>("auth0", "clientSecret") }

    private val conf: Document by lazy {
        val file = File("/PayPredict/conf/auth0.json")
        if (file.exists()) Document.parse(file.readText()) else Document()
    }
}

fun HttpServletRequest.resolve(path: String): String =
    when (val context = contextPath.removePrefix("/")) {
        "" -> "/" + (path.removePrefix("/"))
        else -> "/" + context + "/" + (path.removePrefix("/"))
    }
