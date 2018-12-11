package net.paypredict.patient.cases.view.logs

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.data.provider.DataProvider
import net.paypredict.patient.cases.mongo.*
import org.bson.Document
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.*
import kotlin.collections.set

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class LogSumGrid : Composite<Grid<LogSumItem>>(), ThemableLayout {
    override fun initContent(): Grid<LogSumItem> =
        Grid(LogSumItem::class.java)

    var dateRange: ClosedRange<LocalDate> =
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

    companion object {
        private var isFirstTime = true
        private val SYSTEM_ZONE_ID: ZoneId = ZoneId.systemDefault()
        private val ONE_DAY: Period = Period.ofDays(1)
        private fun LocalDate.toDate(): Date = Date.from(atStartOfDay(SYSTEM_ZONE_ID).toInstant())
        private fun Date.toLocalDate(): LocalDate = toInstant().atZone(SYSTEM_ZONE_ID).toLocalDate()

        private fun ClosedRange<LocalDate>.toFilter() =
            doc {
                val min: Date = start.toDate()
                val max: Date = (endInclusive + ONE_DAY).toDate()
                self["time"] = doc {
                    self[`$gte`] = min
                    self[`$lt`] = max
                }
            }

        private fun build(dateRange: ClosedRange<LocalDate>): List<LogSumItem> =
            casesLog()
                .find(dateRange.toFilter())
                .asSequence()
                .groupBy { it.opt<Date>("time")?.toLocalDate() ?: LocalDate.MIN }
                .map { (date: LocalDate, docs: List<Document>) ->
                    val received =
                        docs.asSequence()
                            .filter { it["action"] == "case.check" }
                            .distinctBy { it["accession"] }
                            .count()
                    val sent =
                        docs.asSequence()
                            .filter { it["action"] == "case.send" }
                            .distinctBy { it["accession"] }
                            .count()

                    LogSumItem(date, received, sent)
                }

        private fun casesLog(): DocumentMongoCollection =
            DBS.Collections
                .casesLog()
                .also {
                    if (isFirstTime) {
                        isFirstTime = false
                        it.createIndex(doc { self["time"] = 1 })
                    }
                }
    }
}

data class LogSumItem(
    val date: LocalDate,
    val received: Int,
    val sent: Int
)