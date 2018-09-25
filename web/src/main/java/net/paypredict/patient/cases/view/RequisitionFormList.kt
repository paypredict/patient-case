package net.paypredict.patient.cases.view

import com.vaadin.flow.component.*
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.data.renderer.IconRenderer
import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.mongo.opt
import org.bson.Document

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/12/2018.
 */
class RequisitionFormList(header: Component? = null) : Composite<VerticalLayout>(), HasSize, HasStyle, ThemableLayout {
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

    private val grid: Grid<RequisitionForm> = Grid<RequisitionForm>().apply {
        addSelectionListener {
            onRequisitionsSelected(it.firstSelectedItem.orElseGet { null })
        }
        addColumn(
            IconRenderer(
                {
                    HtmlComponent("img").apply {
                        element.setAttribute("src", it.thumbnail)
                        element.setAttribute("width", "64")
                        element.setAttribute("height", "64")
                    }
                },
                { "" }
            )
        ).apply {
            width = "75px"
        }
    }

    private fun updateUI(caseId: String?) {
        val requisitionFormList = requisitionFormList(caseId)
        grid.setItems(requisitionFormList)
        if (requisitionFormList.size <= 2) {
            grid.width = null
            grid.height = null
            grid.isHeightByRows = true
        } else {
            grid.isHeightByRows = false
            grid.width = "100%"
            grid.height = "180px"
        }
    }

    private fun requisitionFormList(caseId: String?): List<RequisitionForm> {
        caseId ?: return emptyList()
        val case = DBS.Collections.casesRaw().find(doc {
            doc["_id"] = caseId
        }).firstOrNull()
            ?: return emptyList()
        val accession = case.opt<String>("case", "Case", "accessionNumber")
            ?: return emptyList()
        return DBS.Collections
            .requisitionForms()
            .find(doc { doc["barcode"] = accession })
            .projection(doc {})
            .filterIsInstance<Document>()
            .mapNotNull { it.opt<String>("_id") }
            .map { RequisitionForm(it) }
            .toList()
    }

    init {
        content.width = "120px"
        content.isMargin = false
        content.isPadding = false
        if (header != null) content += header
        content += grid
    }
}

data class RequisitionForm(val formId: String)


val RequisitionForm.jpg: String
    get() = RequisitionFormServlet.baseUrl + "/" + formId + "/JPG"

val RequisitionForm.thumbnail: String
    get() = RequisitionFormServlet.baseUrl + "/" + formId + "/THUMBNAIL"
