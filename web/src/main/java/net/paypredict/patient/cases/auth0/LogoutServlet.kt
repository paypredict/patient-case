package net.paypredict.patient.cases.auth0

import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@WebServlet(
    name = "Auth0 LogoutServlet",
    urlPatterns = ["/logout"]
)
class LogoutServlet : HttpServlet() {

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        if (request.session != null) {
            request.session.invalidate()
        }
        request.contextPath
        response.sendRedirect(request.resolve("/"))
    }

}