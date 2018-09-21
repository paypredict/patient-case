package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.doc
import net.paypredict.patient.cases.data.opt
import net.paypredict.patient.cases.data.worklist.RequisitionForm
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

    var onRequisitionsSelected: (RequisitionForm) -> Unit = {
        Dialog().also { dialog ->
            dialog += H3("Cannot view requisitions yet")
            dialog.open()
        }
    }

    private val attachments = VerticalLayout()

    private fun updateUI(caseId: String?) {
        attachments.removeAll()
        if (caseId == null) return

        val case = DBS.Collections.casesRaw().find(doc { doc["_id"] = caseId }).firstOrNull() ?: return
        val files = case.opt<List<*>>("case", "Case", "Attachments", "File") ?: return
        val fileNameList = files
            .asSequence()
            .filterIsInstance<Document>()
            .filter { it.opt<String>("category") == "Requisition" }
            .mapNotNull { it.opt<String>("fileName") }
            .toList()

        fileNameList.forEach { fileName ->
            attachments += Button(fileName).apply {
                element.setAttribute("theme", "tertiary-inline")
                addClickListener {
                    onRequisitionsSelected(RequisitionForm(fileName))
                }
            }
        }

    }

    init {
        content.isPadding = false
        if (header != null) content += header
        content += attachments
    }
}