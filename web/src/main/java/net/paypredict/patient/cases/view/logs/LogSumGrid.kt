package net.paypredict.patient.cases.view.logs

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.data.provider.DataProvider
import net.paypredict.patient.cases.mongo.opt
import net.paypredict.patient.cases.view.logs.LogSumAction.*
import org.bson.Document
import java.time.LocalDate
import java.time.Period
import java.util.*
import kotlin.reflect.KProperty1

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class LogSumGrid : Composite<Grid<LogSumItem>>(), ThemableLayout {
    override fun initContent(): Grid<LogSumItem> =
        Grid(LogSumItem::class.java).apply {
            val columnsOrder =
                listOf(LogSumItem::date.name) + propertyActions.map { it.first.name }
            setColumns(*columnsOrder.toTypedArray())
            propertyActions.forEach { getColumnByKey(it.first.name).setHeader(it.second.label) }
            addSelectionListener { event ->
                onSelect(event.firstSelectedItem.orElse(null))
            }
        }

    private var dateRange: ClosedRange<LocalDate> =
        (LocalDate.now() - Period.ofDays(30))..LocalDate.now()

    init {
        refresh()
    }

    private fun refresh() {
        content.dataProvider = DataProvider.ofCollection(build(dateRange))
    }

    var width: String?
        get() = content.width
        set(value) {
            content.width = value
        }

    var height: String?
        get() = content.height
        set(value) {
            content.height = value
        }

    var onSelect: (LogSumItem?) -> Unit = {}

    companion object {
        private val propertyActions: List<Pair<KProperty1<LogSumItem, Int>, LogSumAction>> =
            listOf(
                LogSumItem::received to RECEIVED,
                LogSumItem::sent to SENT,
                LogSumItem::passed to SENT_PASSED,
                LogSumItem::resolved to SENT_RESOLVED,
                LogSumItem::timeout to SENT_TIMEOUT
            )

        private fun build(dateRange: ClosedRange<LocalDate>): List<LogSumItem> =
            casesLog()
                .find(dateRange.toLogSumFilter())
                .asSequence()
                .groupBy { it.opt<Date>("time")?.toSystemLocalDate() ?: LocalDate.MIN }
                .map { (date: LocalDate, docs: List<Document>) ->
                    val received =
                        docs.asSequence()
                            .filter { it["action"] == "case.check" }
                            .distinctBy { it["id"] }
                            .count()
                    val sentItems = docs.asSequence()
                        .filter { it["action"] == "case.send" }
                        .distinctBy { it["id"] }

                    val sent =
                        sentItems.count()
                    val passed =
                        sentItems
                            .filter { it.statusIs(SENT_PASSED, SENT_RESOLVED, SENT_TIMEOUT) }
                            .count()
                    val resolved =
                        sentItems
                            .filter { it.statusIs(SENT_RESOLVED) }
                            .count()
                    val timeout =
                        sentItems
                            .filter { it.statusIs(SENT_TIMEOUT) }
                            .count()

                    LogSumItem(
                        date = date,
                        received = received,
                        sent = sent,
                        passed = passed,
                        resolved = resolved,
                        timeout = timeout
                    )
                }
                .sortedByDescending { it.date }
    }
}

