package net.paypredict.patient.cases.auth0

import com.auth0.SessionUtils
import javax.servlet.*
import javax.servlet.annotation.WebFilter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Filter class to check if a valid session exists.
 * This will be true if the User Id is present.
 */
@WebFilter(
    filterName = "Auth0 Filter",
    urlPatterns = ["/portal/*"]
)
class Auth0Filter : Filter {

    override fun init(filterConfig: FilterConfig) {
    }

    /**
     * Perform filter check on this request - verify the User Id is present.
     *
     * @param request  the received request
     * @param response the response to send
     * @param next     the next filter chain
     */
    override fun doFilter(request: ServletRequest, response: ServletResponse, next: FilterChain) {
        val req = request as HttpServletRequest
        val res = response as HttpServletResponse
        val accessToken = SessionUtils.get(req, "accessToken") as? String
        val idToken = SessionUtils.get(req, "idToken") as? String
        if (accessToken == null && idToken == null) {
            SessionUtils.set(req, "redirectOnSuccess", req.resolve(req.pathInfo))
            res.sendRedirect(req.resolve("login"))
            return
        }
        next.doFilter(request, response)
    }

    override fun destroy() {}
}