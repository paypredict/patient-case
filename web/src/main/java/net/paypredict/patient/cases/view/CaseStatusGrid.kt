package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.icon.Icon
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.data.provider.DataProvider
import com.vaadin.flow.data.provider.Query
import com.vaadin.flow.data.provider.QuerySortOrder
import com.vaadin.flow.data.provider.SortDirection
import com.vaadin.flow.data.renderer.IconRenderer
import com.vaadin.flow.data.renderer.LocalDateTimeRenderer
import com.vaadin.flow.data.selection.SelectionEvent
import com.vaadin.flow.shared.Registration
import net.paypredict.patient.cases.data.worklist.CASE_STATUS_META_DATA_MAP
import net.paypredict.patient.cases.data.worklist.CaseStatus
import net.paypredict.patient.cases.data.worklist.Status
import net.paypredict.patient.cases.data.worklist.toCaseStatus
import net.paypredict.patient.cases.ifHasDocKey
import net.paypredict.patient.cases.ifSortable
import net.paypredict.patient.cases.mongo.*
import org.bson.Document
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.set
import kotlin.reflect.jvm.javaType

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class CaseStatusGrid : Composite<Grid<CaseStatus>>(), ThemableLayout {
    override fun initContent(): Grid<CaseStatus> =
        Grid(CaseStatus::class.java)

    private var filter = doc { }

    init {
        content.setColumns(*CASE_STATUS_META_DATA_MAP.entries.sortedBy { it.value.view.order }.map { it.key }.toTypedArray())
        for (column in content.columns) {
            val meta = CASE_STATUS_META_DATA_MAP[column.key] ?: continue
            column.isVisible = meta.view.isVisible
            column.flexGrow = meta.view.flexGrow
            if (meta.view.sortable)
                column.setSortProperty(meta.prop.name)
            when (meta.prop.returnType.javaType) {
                Date::class.java -> {
                    column.isVisible = false
                    content.addColumn(
                        LocalDateTimeRenderer(
                            { it.date?.toInstant()?.atZone(ZoneOffset.UTC)?.toLocalDateTime() },
                            dateTimeFormat
                        )
                    ).apply {
                        setHeader(meta.view.label)
                        if (meta.view.sortable)
                            setSortProperty(meta.prop.name)
                    }
                }
                Status::class.javaObjectType -> {
                    column.isVisible = false
                    content.addColumn(
                        IconRenderer(
                            {
                                when ((meta.prop.get(it) as? Status)?.value?.toUpperCase()) {
                                    "SAVED" -> Icon(VaadinIcon.WARNING).apply { color = "#f4b400" }
                                    "INFO" -> Icon(VaadinIcon.CHECK).apply { color = "#1e8e3e" }
                                    "WARNING" -> Icon(VaadinIcon.WARNING).apply { color = "#f4b400" }
                                    "ERROR" -> Icon(VaadinIcon.EXCLAMATION_CIRCLE).apply { color = "#d23f31" }
                                    "NAME UPDATED" -> Icon(VaadinIcon.WARNING).apply { color = "#f4b400" }
                                    "AUTO FIXED" -> Icon(VaadinIcon.WARNING).apply { color = "#f4b400" }
                                    "PASS" -> Icon(VaadinIcon.CHECK_CIRCLE).apply { color = "#1e8e3e" }
                                    null -> Icon(VaadinIcon.BAN).apply { isVisible = false }
                                    else -> Icon(VaadinIcon.BAN).apply { color = "#d23f31" }
                                }.apply {
                                    style["width"] = "1em"
                                    style["height"] = "1em"
                                }
                            },
                            { "" })
                    ).apply {
                        setHeader(meta.view.label)
                        if (meta.view.sortable)
                            setSortProperty(meta.prop.name)
                        flexGrow = 0
                        width = "75px"
                    }
                }
            }
        }
        refresh()
    }

    fun filter(
        viewOnlyUnresolved: Boolean = false
    ) {
        filter = when {
            viewOnlyUnresolved -> doc {
                doc[`$and`] = listOf(
                    doc {
                        doc["status.problems"] = doc { doc[`$gt`] = 0 }
                    },
                    doc {
                        doc["status.value"] = doc { doc[`$ne`] = "RESOLVED" }
                    }
                )
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
                    .projection(projection)
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
        private val projection =
            doc {
                for (metaData in CASE_STATUS_META_DATA_MAP.values) {
                    metaData.view.ifHasDocKey { doc[it] = 1 }
                }
            }

        private fun collection() = DBS.Collections.casesRaw().apply {
            for (metaData in CASE_STATUS_META_DATA_MAP.values) {
                metaData.view.ifHasDocKey { docKey ->
                    createIndex(doc { doc[docKey] = 1 })
                }
            }
        }

        private fun Query<CaseStatus, *>.toMongoSort(): Document {
            val sortOrders =
                if (sortOrders.isNotEmpty())
                    sortOrders else
                    listOf(QuerySortOrder("date", SortDirection.DESCENDING))
            return doc {
                sortOrders.forEach { sortOrder: QuerySortOrder ->
                    CASE_STATUS_META_DATA_MAP[sortOrder.sorted]?.view?.ifSortable { sortKey ->
                        doc[sortKey] = when (sortOrder.direction) {
                            null,
                            SortDirection.ASCENDING -> 1
                            SortDirection.DESCENDING -> -1
                        }
                    }
                }
            }
        }
    }
}