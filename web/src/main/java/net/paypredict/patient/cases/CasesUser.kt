package net.paypredict.patient.cases

import com.vaadin.flow.component.UI
import javax.servlet.http.HttpServletRequest

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 11/28/2018.
 */
data class CasesUser(
    val username: String?,
    val email: String
)


var UI.casesUser
    get(): CasesUser? =
        session.session.getAttribute("casesUser") as? CasesUser
    set(value) {
        session.session.setAttribute("casesUser", value)
    }

var HttpServletRequest.casesUser
    get(): CasesUser? =
        session.getAttribute("casesUser") as? CasesUser
    set(value) {
        session.setAttribute("casesUser", value)
    }
