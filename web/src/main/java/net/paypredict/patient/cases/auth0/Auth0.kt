package net.paypredict.patient.cases.auth0

import com.auth0.AuthenticationController
import net.paypredict.patient.cases.mongo.opt
import org.bson.Document
import java.io.File
import javax.servlet.http.HttpServletRequest
import com.auth0.client.auth.AuthAPI
import com.auth0.client.mgmt.ManagementAPI
import com.auth0.json.auth.TokenHolder


object Auth0 {
    sealed class App(private val name: String) {
        object Web : App("web") {
            fun authenticationController(): AuthenticationController =
                AuthenticationController
                    .newBuilder(domain, clientId, clientSecret)
                    .build()
        }

        object Users : App("users") {
            fun managementAPI(
                authAPI: AuthAPI =
                    authAPI(),
                tokenHolder: TokenHolder =
                    authAPI
                        .requestToken("https://$domain/api/v2/")
                        .execute()
            ): ManagementAPI =
                ManagementAPI(domain, tokenHolder.accessToken)
        }

        protected val clientId: String? by lazy { conf.opt<String>("auth0", "apps", name, "clientId") }
        protected val clientSecret: String? by lazy { conf.opt<String>("auth0", "apps", name, "clientSecret") }

        fun authAPI(): AuthAPI =
            AuthAPI(domain, clientId, clientSecret)
    }


    val domain: String? by lazy { conf.opt<String>("auth0", "domain") }

    private val conf: Document by lazy {
        val file = File("/PayPredict/conf/auth0.json")
        if (file.exists()) Document.parse(file.readText()) else Document()
    }
}

internal fun HttpServletRequest.resolve(path: String): String =
    when (val context = contextPath.removePrefix("/")) {
        "" -> "/" + (path.removePrefix("/"))
        else -> "/" + context + "/" + (path.removePrefix("/"))
    }
