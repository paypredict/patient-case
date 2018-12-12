package net.paypredict.patient.cases.view.logs

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.tabs.Tab
import com.vaadin.flow.component.tabs.Tabs
import net.paypredict.patient.cases.mongo.`$in`
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.view.CaseAttrGrid
import net.paypredict.patient.cases.view.logs.LogSumAction.*
import net.paypredict.patient.cases.view.plusAssign

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
}

