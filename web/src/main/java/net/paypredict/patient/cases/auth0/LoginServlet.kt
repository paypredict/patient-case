package net.paypredict.patient.cases.auth0

import com.auth0.AuthenticationController
import java.io.UnsupportedEncodingException
import javax.servlet.ServletConfig
import javax.servlet.ServletException
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet(
    name = "Auth0 LoginServlet",
    urlPatterns = ["/login"]
)
class LoginServlet : HttpServlet() {

    private val domain: String? = Auth0.domain
    private lateinit var authenticationController: AuthenticationController

    override fun init(config: ServletConfig) {
        super.init(config)
        try {
            authenticationController = Auth0.buildAuthenticationController()
        } catch (e: UnsupportedEncodingException) {
            throw ServletException(
                "Couldn't create the AuthenticationController instance. Check the configuration.",
                e
            )
        }
    }

    override fun doGet(req: HttpServletRequest, res: HttpServletResponse) {
        val redirectUri: String =
            with(req) {
                val context: String =
                    when (val it = contextPath.removePrefix("/")) {
                        "" -> it
                        else -> "/$it"
                    }
                val port: String =
                    when {
                        scheme == "http" && serverPort == 80 || serverPort == -1 -> ""
                        scheme == "https" && serverPort == 443 || serverPort == -1 -> ""
                        else -> ":$serverPort"
                    }

                "$scheme://$serverName$port$context/callback"
            }

        val authorizeUrl: String =
            authenticationController
                .buildAuthorizeUrl(req, redirectUri)
                .withAudience("https://$domain/userinfo")
                .build()

        res.sendRedirect(authorizeUrl)
    }

}