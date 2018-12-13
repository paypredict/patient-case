package net.paypredict.patient.cases.view.logs

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.html.Anchor
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.tabs.Tab
import com.vaadin.flow.component.tabs.Tabs
import com.vaadin.flow.server.InputStreamFactory
import com.vaadin.flow.server.StreamResource
import net.paypredict.patient.cases.data.workbook.WorkbookContext
import net.paypredict.patient.cases.mongo.`$in`
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.view.CaseAttrGrid
import net.paypredict.patient.cases.view.logs.LogSumAction.*
import net.paypredict.patient.cases.view.plusAssign
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.bson.Document
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 12/12/2018.
 */
class LogDetailsView : Composite<VerticalLayout>() {
    private val actionTabs: List<Pair<LogSumAction, Tab>> =
        values().map { it to Tab(it.label) }

    private val tabs: Tabs =
        Tabs().apply {
            actionTabs.forEach { add(it.second) }
            addSelectedChangeListener { showDetails() }
        }
    private val downloadButton =
        Button("", VaadinIcon.DOWNLOAD.create()).apply {
            style["cursor"] = "pointer"
        }
    private val downloadAnchor =
        Anchor("#", "").apply {
            isVisible = false
            this += downloadButton
        }

    private val header: HorizontalLayout =
        HorizontalLayout().apply {
            isPadding = true
            width = "100%"
            this += tabs
            this += downloadAnchor
            setFlexGrow(1.0, tabs)
        }

    private val casesGrid =
        CaseAttrGrid(isEnabled = false).apply {
            width = "100%"
            height = "100%"
            element.style["border"] = "none"
        }

    private var item: LogSumItem? = null

    private val action: LogSumAction
        get() = tabs
            .selectedTab
            ?.let { selectedTab ->
                actionTabs
                    .firstOrNull { it.second == selectedTab }
                    ?.first
            }
            ?: RECEIVED


    fun showDetails(item: LogSumItem? = this.item) {
        this.item = item
        downloadAnchor.isVisible = item != null
        casesGrid.isEnabled = item != null
        if (item == null) {
            casesGrid.refresh()
            return
        }
        item.toStreamResource().also {
            downloadAnchor.setHref(it)
            downloadButton.text = it.name
        }

        casesGrid.filter(newFilter = action.toCasesGridFilter(item))
        casesGrid.refresh()
    }

    init {
        content.isPadding = false
        content.isSpacing = false
        content += header
        content += casesGrid
        content.setFlexGrow(1.0, casesGrid)
    }

    private fun LogSumItem.toStreamResource(): StreamResource {
        val name = "logs-$date.xlsx"
        return StreamResource(name, InputStreamFactory { buildLogExcelFile() })
    }

    private fun buildLogExcelFile(): InputStream {
        val item = item!!
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            actionTabs.forEach { (action, _) ->
                casesGrid.export(
                    WorkbookContext(workbook),
                    workbook.createSheet(action.label),
                    action.toCasesGridFilter(item)
                )
            }

            workbook.write(out)
        }
        return out.toByteArray().inputStream()
    }

    companion object {
        private fun LogSumAction.toCasesGridFilter(item: LogSumItem): Document {
            val dayLogItems =
                casesLog()
                    .find((item.date..item.date).toLogSumFilter())
                    .projection(doc {
                        self["id"] = 1
                        self["action"] = 1
                        self["status"] = 1
                    })
                    .asSequence()


            val actionLogItems =
                when (this) {
                    RECEIVED -> dayLogItems.filter { it["action"] == "case.check" }
                    SENT -> dayLogItems.filter { it["action"] == "case.send" }
                    SENT_PASSED -> dayLogItems.filter {
                        it["action"] == "case.send" && it.statusIs(SENT_PASSED, SENT_RESOLVED, SENT_TIMEOUT)
                    }
                    SENT_RESOLVED -> dayLogItems.filter {
                        it["action"] == "case.send" && it.statusIs(SENT_RESOLVED)
                    }
                    SENT_TIMEOUT -> dayLogItems.filter {
                        it["action"] == "case.send" && it.statusIs(SENT_TIMEOUT)
                    }
                }

            val caseIds = actionLogItems.map { it["id"] as String }.toSet()

            return doc { self["_id"] = doc { self[`$in`] = caseIds } }
        }
    }
}

