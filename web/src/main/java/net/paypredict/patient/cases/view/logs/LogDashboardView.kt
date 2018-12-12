package net.paypredict.patient.cases.view.logs

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.html.Anchor
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.splitlayout.SplitLayout
import com.vaadin.flow.server.InputStreamFactory
import com.vaadin.flow.server.StreamResource
import net.paypredict.patient.cases.view.plusAssign
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.io.InputStream

class LogDashboardView : Composite<SplitLayout>() {
    private val logSumGridActions: HorizontalLayout =
        HorizontalLayout().apply {
            isPadding = true
            defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
            val fileName = "logs.xlsx"
            this += Anchor(StreamResource(fileName, InputStreamFactory { buildLogExcelFile() }), "").apply {
                this += Button(fileName, VaadinIcon.DOWNLOAD.create()).apply { style["cursor"] = "pointer" }
            }
        }

    private val logSumGrid: LogSumGrid =
        LogSumGrid().apply {
            height = "100%"
            element.style["border"] = "none"
            onSelect = {
                details.showDetails(it)
            }
        }

    private val details: LogDetailsView =
        LogDetailsView()

    init {
        val layoutLeft: VerticalLayout =
            VerticalLayout()
                .apply {
                    isPadding = false
                    isSpacing = false
                    setSizeFull()
                    this += logSumGridActions
                    this += logSumGrid
                }

        with(content) {
            setSizeFull()
            orientation = SplitLayout.Orientation.HORIZONTAL
            addToPrimary(layoutLeft)
            addToSecondary(details)
        }
    }

    private fun buildLogExcelFile(): InputStream {
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            logSumGrid.export(workbook)
            workbook.write(out)
        }
        return out.toByteArray().inputStream()
    }
}
