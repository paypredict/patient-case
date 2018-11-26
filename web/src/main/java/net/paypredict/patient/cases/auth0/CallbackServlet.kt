package net.paypredict.patient.cases.auth0

import com.auth0.AuthenticationController
import com.auth0.IdentityVerificationException
import com.auth0.SessionUtils
import java.io.IOException
import java.io.UnsupportedEncodingException
import javax.servlet.ServletConfig
import javax.servlet.ServletException
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


    /**
     * Initialize this servlet with required configuration.
     *
     *
     * Parameters needed on the Local Servlet scope:
     *
     *  * 'com.auth0.redirect_on_success': where to redirect after a successful authentication.
     *  * 'com.auth0.redirect_on_error': where to redirect after a failed authentication.
     *
     * Parameters needed on the Local/Global Servlet scope:
     *
     *  * 'com.auth0.domain': the Auth0 domain.
     *  * 'com.auth0.client_id': the Auth0 Client id.
     *  * 'com.auth0.client_secret': the Auth0 Client secret.
     *
     */
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

    /**
     * Process a call to the redirect_uri with a GET HTTP method.
     *
     * @param req the received request with the tokens (implicit grant) or the authorization code (code grant) in the parameters.
     * @param res the response to send back to the server.
     * @throws IOException
     * @throws ServletException
     */
    public override fun doGet(req: HttpServletRequest, res: HttpServletResponse) {
        handle(req, res)
    }


    /**
     * Process a call to the redirect_uri with a POST HTTP method. This occurs if the authorize_url included the 'response_mode=form_post' value.
     * This is disabled by default. On the Local Servlet scope you can specify the 'com.auth0.allow_post' parameter to enable this behaviour.
     *
     * @param req the received request with the tokens (implicit grant) or the authorization code (code grant) in the parameters.
     * @param res the response to send back to the server.
     * @throws IOException
     * @throws ServletException
     */
    public override fun doPost(req: HttpServletRequest, res: HttpServletResponse) {
        handle(req, res)
    }

    private fun handle(req: HttpServletRequest, res: HttpServletResponse) {
        try {
            val tokens = authenticationController.handle(req)
            SessionUtils.set(req, "accessToken", tokens.accessToken)
            SessionUtils.set(req, "idToken", tokens.idToken)
            val redirectOnSuccess = SessionUtils.get(req, "redirectOnSuccess") as? String ?: ""
            res.sendRedirect(redirectOnSuccess)
            SessionUtils.remove(req, "redirectOnSuccess")
        } catch (e: IdentityVerificationException) {
            e.printStackTrace()
            res.sendRedirect(req.resolve(""))
        }
    }

}