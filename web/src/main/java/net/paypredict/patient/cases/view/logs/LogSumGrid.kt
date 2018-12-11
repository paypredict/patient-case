package net.paypredict.patient.cases.view.logs

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.data.provider.DataProvider
import net.paypredict.patient.cases.mongo.opt
import org.bson.Document
import java.time.LocalDate
import java.time.Period
import java.util.*

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class LogSumGrid : Composite<Grid<LogSumItem>>(), ThemableLayout {
    override fun initContent(): Grid<LogSumItem> =
        Grid(LogSumItem::class.java).apply {
            setColumns(
                LogSumItem::date.name,
                LogSumItem::received.name,
                LogSumItem::sent.name,
                LogSumItem::resolved.name,
                LogSumItem::timeout.name
            )
            addSelectionListener {
                onSelect(it.firstSelectedItem.orElseGet { null })
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
                    val resolved =
                        sentItems
                            .filter { it.opt<Boolean>("status", "resolved") == true }
                            .count()
                    val timeout =
                        sentItems
                            .filter { it.opt<Boolean>("status", "timeout") == true }
                            .count()

                    LogSumItem(date, received, sent, resolved, timeout)
                }
    }
}

