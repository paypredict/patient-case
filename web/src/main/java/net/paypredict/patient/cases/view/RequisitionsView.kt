package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.grid.Grid
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

    var onRequisitionsSelected: (RequisitionForm?) -> Unit = {
        Dialog().also { dialog ->
            dialog += H3("Cannot view requisitions yet")
            dialog.open()
        }
    }

    private val grid: Grid<RequisitionForm> = Grid<RequisitionForm>(RequisitionForm::class.java).apply {
        addSelectionListener {
            onRequisitionsSelected(it.firstSelectedItem.orElseGet { null })
        }
    }

    private fun updateUI(caseId: String?) {
        val requisitionFormList = requisitionFormList(caseId)
        grid.setItems(requisitionFormList)
        grid.isHeightByRows = requisitionFormList.size < 8
        grid.width = (requisitionFormList.asSequence().map { it.fileName.length }.max() ?: 32).let {
            "%.1fem".format(it * 0.5)
        }
    }

    private fun requisitionFormList(caseId: String?): List<RequisitionForm> {
        caseId ?: return emptyList()
        val case = DBS.Collections.casesRaw().find(doc { doc["_id"] = caseId }).firstOrNull()
            ?: return emptyList()
        val files = case.opt<List<*>>("case", "Case", "Attachments", "File")
            ?: return emptyList()
        return files
            .asSequence()
            .filterIsInstance<Document>()
            .filter { it.opt<String>("category") == "Requisition" }
            .mapNotNull { it.opt<String>("fileName") }
            .map { RequisitionForm(it) }
            .toList()
    }

    init {
        content.isPadding = false
        if (header != null) content += header
        content += grid
    }
}