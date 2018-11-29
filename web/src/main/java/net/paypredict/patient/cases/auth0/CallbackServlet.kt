package net.paypredict.patient.cases.auth0

import com.auth0.AuthenticationController
import com.auth0.IdentityVerificationException
import com.auth0.SessionUtils
import com.auth0.Tokens
import com.auth0.json.mgmt.users.User
import net.paypredict.patient.cases.CasesUser
import net.paypredict.patient.cases.casesUser
import javax.servlet.ServletConfig
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * The Servlet endpoint used as the callback handler in the OAuth 2.0 authorization code grant flow.
 * It will be called with the authorization code after a successful login.
 */
@WebServlet(
    name = "Auth0 CallbackServlet",
    urlPatterns = ["/callback"]
)
class CallbackServlet : HttpServlet() {

    private lateinit var authenticationController: AuthenticationController

    override fun init(config: ServletConfig) {
        super.init(config)
        authenticationController = Auth0.App.Web.authenticationController()
    }

    public override fun doGet(req: HttpServletRequest, res: HttpServletResponse) {
        handle(req, res)
    }


    public override fun doPost(req: HttpServletRequest, res: HttpServletResponse) {
        handle(req, res)
    }

    private fun handle(req: HttpServletRequest, res: HttpServletResponse) {
        try {
            val tokens: Tokens = authenticationController.handle(req)
            SessionUtils.set(req, "accessToken", tokens.accessToken)
            SessionUtils.set(req, "idToken", tokens.idToken)
            req.casesUser = tokens.requestCasesUser()

            val redirectOnSuccess = SessionUtils.get(req, "redirectOnSuccess") as? String ?: ""
            res.sendRedirect(redirectOnSuccess)
            SessionUtils.remove(req, "redirectOnSuccess")
        } catch (e: IdentityVerificationException) {
            e.printStackTrace()
            res.sendRedirect(req.resolve(""))
        }
    }

    private fun Tokens.requestCasesUser(): CasesUser? {
        val userInfo =
            Auth0.App.Web.authAPI()
                .userInfo(accessToken)
                .execute()
        val userId =
            userInfo.values["sub"] as? String
                ?: return null
        val user: User =
            Auth0.App.Users
                .managementAPI()
                .users()
                .get(userId, null)
                .execute()
                ?: return null
        return CasesUser(
            username = user.username,
            email = user.email
        )
    }

}