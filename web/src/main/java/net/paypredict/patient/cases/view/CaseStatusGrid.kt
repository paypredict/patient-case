package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.icon.Icon
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.data.provider.DataProvider
import com.vaadin.flow.data.provider.Query
import com.vaadin.flow.data.provider.QuerySortOrder
import com.vaadin.flow.data.provider.SortDirection
import com.vaadin.flow.data.renderer.IconRenderer
import com.vaadin.flow.data.renderer.LocalDateTimeRenderer
import com.vaadin.flow.data.selection.SelectionEvent
import com.vaadin.flow.shared.Registration
import net.paypredict.patient.cases.bson.`$ne`
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.doc
import net.paypredict.patient.cases.data.worklist.CASE_STATUS_META_DATA_MAP
import net.paypredict.patient.cases.data.worklist.CaseStatus
import net.paypredict.patient.cases.data.worklist.Status
import net.paypredict.patient.cases.data.worklist.toCaseStatus
import org.bson.Document
import org.bson.conversions.Bson
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.set
import kotlin.reflect.jvm.javaType

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class CaseStatusGrid : Composite<Grid<CaseStatus>>() {
    override fun initContent(): Grid<CaseStatus> =
        Grid(CaseStatus::class.java)

    private var filter = doc { }

    init {
        content.setColumns(*CASE_STATUS_META_DATA_MAP.entries.sortedBy { it.value.view.order }.map { it.key }.toTypedArray())
        for (column in content.columns) {
            val meta = CASE_STATUS_META_DATA_MAP[column.key] ?: continue
            column.isVisible = meta.view.isVisible
            when (meta.prop.returnType.javaType) {
                Date::class.java -> {
                    column.isVisible = false
                    content.addColumn(
                        LocalDateTimeRenderer(
                            { it.date?.toInstant()?.atZone(ZoneOffset.UTC)?.toLocalDateTime() },
                            dateTimeFormat
                        )
                    ).apply {
                        setHeader(meta.view.caption)
                        setSortProperty(meta.prop.name)
                    }
                }
                Status::class.javaObjectType -> {
                    column.isVisible = false
                    content.addColumn(
                        IconRenderer(
                            {
                                when ((meta.prop.get(it) as? Status)?.value?.toUpperCase()) {
                                    "SAVED" -> Icon(VaadinIcon.WARNING).apply { color = "gold" }
                                    "WARNING" -> Icon(VaadinIcon.WARNING).apply { color = "gold" }
                                    "AUTO_FIXED" -> Icon(VaadinIcon.WARNING).apply { color = "gold" }
                                    "ERROR" -> Icon(VaadinIcon.EXCLAMATION_CIRCLE).apply { color = "red" }
                                    "PASS" -> Icon(VaadinIcon.CHECK_CIRCLE).apply { color = "lightgreen" }
                                    null -> Icon(VaadinIcon.BAN).apply { color = "lightgray" }
                                    else -> Icon(VaadinIcon.BAN).apply { color = "red" }
                                }
                            },
                            { "" })
                    ).apply {
                        setHeader(meta.view.caption)
                        flexGrow = 0
                        width = "75px"
                    }
                }
            }
        }
        refresh()
    }

    fun filter(
        viewOnlyUnsolved: Boolean = false
    ) {
        filter = when {
            viewOnlyUnsolved -> doc {
                doc["status.value"] = doc { doc[`$ne`] = "SOLVED" }
            }
            else -> doc { }
        }
        refresh()
    }


    fun addSelectionListener(listener: (SelectionEvent<Grid<CaseStatus>, CaseStatus>) -> Unit): Registration =
        content.addSelectionListener(listener)

    fun refreshItem(item: CaseStatus) {
        content.dataProvider.refreshItem(item)
    }

    fun refresh() {
        content.dataProvider = DataProvider.fromFilteringCallbacks(
            { query: Query<CaseStatus, Unit> ->
                collection()
                    .find(filter)
                    .sort(query.toMongoSort())
                    .skip(query.offset)
                    .limit(query.limit)
                    .map { it.toCaseStatus() }
                    .toList()
                    .stream()
            },
            { collection().count(filter).toInt() }
        )
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
        private val dateTimeFormat: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM dd yyyy hh:mm")

        private fun collection() = DBS.Collections.casesRaw().apply {
            createIndex(doc { doc["date"] = 1 })
            createIndex(doc { doc["status.value"] = 1 })
        }

        private fun Query<CaseStatus, *>.toMongoSort(): Bson? {
            val sortOrders = if (sortOrders.isNotEmpty())
                sortOrders else
                listOf(QuerySortOrder("date", SortDirection.DESCENDING))
            return Document().also { document ->
                sortOrders.forEach { sortOrder: QuerySortOrder ->
                    val docKey = CASE_STATUS_META_DATA_MAP[sortOrder.sorted]?.run {
                        if (view.docKey.isNotBlank()) view.docKey else prop.name
                    }
                    document[docKey] = when (sortOrder.direction) {
                        null,
                        SortDirection.ASCENDING -> 1
                        SortDirection.DESCENDING -> -1
                    }
                }
            }
        }
    }
}