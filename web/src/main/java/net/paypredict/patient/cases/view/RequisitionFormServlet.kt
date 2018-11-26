package net.paypredict.patient.cases.view

import net.paypredict.patient.cases.PatientCases
import java.io.File
import javax.servlet.ServletConfig
import javax.servlet.annotation.WebServlet
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 *
 * Created by alexei.vylegzhanin@gmail.com on 9/24/2018.
 */
@WebServlet(
    name = "RequisitionForm Servlet",
    urlPatterns = ["/portal/requisition-form/*"],
    loadOnStartup = 1
)
class RequisitionFormServlet : HttpServlet() {

    override fun init(config: ServletConfig?) {
        super.init(config)
        baseUrl_ = servletContext.contextPath.removeSuffix("/") + "/portal/requisition-form"
    }

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        response.status = 404
        val path = request.pathInfo.split('/')
        if (path.size != 3) return
        val formId = path[1]
        val resType = path[2]

        val formDir = dir.resolve(formId.substring(0, 4))
            .resolve(formId)
            .apply { if (!isDirectory) return }


        val file = formDir
            .resolve(
                when (resType) {
                    "PNG" -> "requisition.png"
                    "JPG" -> "requisition.jpg"
                    "THUMBNAIL" -> "thumbnail.jpg"
                    else -> return
                }
            )
            .apply { if (!isFile) return }

        response.status = 200
        response.contentType = "image/jpg"
        response.setContentLengthLong(file.length())
        response.outputStream.write(file.readBytes())
    }

    companion object {
        private val dir: File by lazy { PatientCases.clientDir.resolve("requisitionForms") }
        private var baseUrl_: String = "/requisition-form"
        val baseUrl: String get() = baseUrl_
    }
}

