package net.paypredict.patient.cases.view.logs

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.tabs.Tab
import com.vaadin.flow.component.tabs.Tabs
import net.paypredict.patient.cases.mongo.`$in`
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.mongo.opt
import net.paypredict.patient.cases.view.CaseAttrGrid
import net.paypredict.patient.cases.view.plusAssign
import kotlin.reflect.KProperty1

typealias LogSumItemProperty = KProperty1<LogSumItem, Int>

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 12/12/2018.
 */
class LogDetailsView : Composite<VerticalLayout>() {
    private val receivedTab = LogSumItem::received.toTab()
    private val sentTab = LogSumItem::sent.toTab()
    private val resolvedTab = LogSumItem::resolved.toTab()
    private val timeoutTab = LogSumItem::timeout.toTab()

    private val tabs: Tabs =
        Tabs(receivedTab, sentTab, resolvedTab, timeoutTab)
            .apply {
                addSelectedChangeListener {
                    showDetails()
                }
            }

    private val casesGrid = CaseAttrGrid(isEnabled = false).apply {
        width = "100%"
        height = "100%"
        element.style["border"] = "none"
    }

    private var item: LogSumItem? = null

    private val action: LogSumAction
        get() =
            when (tabs.selectedTab) {
                receivedTab -> LogSumAction.RECEIVED
                sentTab -> LogSumAction.SENT
                resolvedTab -> LogSumAction.RESOLVED
                timeoutTab -> LogSumAction.TIMEOUT
                else -> LogSumAction.RECEIVED
            }

    fun showDetails(item: LogSumItem? = this.item) {
        this.item = item
        casesGrid.isEnabled = item != null
        if (item == null) {
            casesGrid.refresh()
            return
        }

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
            when (action) {
                LogSumAction.RECEIVED -> dayLogItems.filter { it["action"] == "case.check" }
                LogSumAction.SENT -> dayLogItems.filter { it["action"] == "case.send" }
                LogSumAction.RESOLVED -> dayLogItems.filter {
                    it["action"] == "case.send" && it.opt<Boolean>("status", "resolved") == true
                }
                LogSumAction.TIMEOUT -> dayLogItems.filter {
                    it["action"] == "case.send" && it.opt<Boolean>("status", "timeout") == true
                }
            }

        val caseIds = actionLogItems.map { it["id"] as String }.toSet()

        val casesFilter = doc { self["_id"] = doc { self[`$in`] = caseIds } }

        casesGrid.filter(newFilter = casesFilter)
        casesGrid.refresh()
    }

    init {
        content.isPadding = false
        content += tabs
        content += casesGrid
        content.setFlexGrow(1.0, casesGrid)
    }

    companion object {
        private fun LogSumItemProperty.toTab(): Tab =
            Tab(name.capitalize())
    }
}

