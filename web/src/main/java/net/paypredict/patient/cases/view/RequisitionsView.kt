package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.doc
import net.paypredict.patient.cases.data.opt
import org.bson.Document

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/12/2018.
 */
class RequisitionsView(header: Component? = null) : Composite<VerticalLayout>(), HasSize, ThemableLayout {
    var caseId: String? = null
        set(value) {
            field = value
            updateUI(value)
        }

    private val attachments = VerticalLayout()

    private fun updateUI(caseId: String?) {
        attachments.removeAll()
        if (caseId == null) return

        val case = DBS.Collections.casesRaw().find(doc { doc["_id"] = caseId }).firstOrNull() ?: return
        val files = case.opt<List<*>>("case", "Case", "Attachments", "File") ?: return
        val fileNameList = files
            .filterIsInstance<Document>()
            .filter { it.opt<String>("category") == "Requisition" }
            .mapNotNull { it.opt<String>("fileName") }

        fileNameList.forEach {
            attachments += Button(it).apply {
                element.setAttribute("theme", "tertiary-inline")
            }
        }

    }

    init {
        content.isPadding = false
        if (header != null) content += header
        content += attachments
    }
}