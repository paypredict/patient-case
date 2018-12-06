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
import net.paypredict.patient.cases.data.worklist.*
import net.paypredict.patient.cases.ifHasDocKey
import net.paypredict.patient.cases.ifHasFilterKeys
import net.paypredict.patient.cases.ifSortable
import net.paypredict.patient.cases.mongo.*
import net.paypredict.patient.cases.mongo.DBS.Collections.cases
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
class CaseAttrGrid : Composite<Grid<CaseAttr>>(), ThemableLayout {
    override fun initContent(): Grid<CaseAttr> =
        Grid(CaseAttr::class.java)

    private var filter: Document = doc { }

    init {
        content.setColumns(*CASE_ATTR_META_DATA_MAP.entries
            .asSequence()
            .sortedBy { it.value.view.order }
            .map { it.key }
            .toList()
            .toTypedArray())
        for (column in content.columns) {
            val meta = CASE_ATTR_META_DATA_MAP[column.key] ?: continue
            column.isVisible = meta.view.isVisible
            column.flexGrow = meta.view.flexGrow
            if (meta.view.sortable)
                column.setSortProperty(meta.prop.name)
            val propJavaType = meta.prop.returnType.javaType
            when {
                propJavaType == Date::class.javaObjectType -> {
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
                IssuesStatus::class.java.isAssignableFrom(propJavaType as Class<*>) -> {
                    column.isVisible = false
                    content.addColumn(
                        IconRenderer(
                            {
                                infix fun VaadinIcon.color(iconColor: String): Icon =
                                    Icon(this).apply {
                                        color = iconColor
                                        style["width"] = "1em"
                                        style["height"] = "1em"
                                    }

                                when ((meta.prop.get(it) as? IssuesStatus)?.type) {
                                    IssuesStatus.Type.OK -> VaadinIcon.CHECK color "#1e8e3e"
                                    IssuesStatus.Type.INFO -> VaadinIcon.CHECK_CIRCLE color "#1e8e3e"
                                    IssuesStatus.Type.WARN -> VaadinIcon.WARNING color "#f4b400"
                                    IssuesStatus.Type.QUESTION -> VaadinIcon.QUESTION_CIRCLE color "#c0c0c0"
                                    IssuesStatus.Type.ERROR -> VaadinIcon.EXCLAMATION_CIRCLE color "#d23f31"
                                    null -> (VaadinIcon.BAN color "red").apply { isVisible = false }
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
        viewOnlyUnsent: Boolean = false,
        newFilter: Document? = null
    ) {
        filter = when {
            newFilter != null ->
                newFilter
            viewOnlyUnsent ->
                doc {
                    self["status.value"] = "CHECKED"
                }
            else ->
                doc {
                    self["status.checked"] = true
                    self["status.timeout"] = false
                }
        }
        refresh()
    }


    fun addSelectionListener(listener: (SelectionEvent<Grid<CaseAttr>, CaseAttr>) -> Unit): Registration =
        content.addSelectionListener(listener)

    fun refreshItem(item: CaseAttr) {
        content.dataProvider.refreshItem(item)
    }

    fun refresh() {
        content.dataProvider = DataProvider.fromFilteringCallbacks(
            { query: Query<CaseAttr, Unit> ->
                collection()
                    .find(filter)
                    .projection(projection)
                    .sort(query.toMongoSort())
                    .skip(query.offset)
                    .limit(query.limit)
                    .map { it.toCaseAttr() }
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
                for (metaData in CASE_ATTR_META_DATA_MAP.values) {
                    metaData.view.ifHasDocKey { self[it] = 1 }
                }
            }

        private fun collection() = cases().apply {
            for (metaData in CASE_ATTR_META_DATA_MAP.values) {
                metaData.view.ifHasDocKey { docKey ->
                    createIndex(doc { self[docKey] = 1 })
                }
                metaData.view.ifHasFilterKeys { filterKeys ->
                    createIndex(doc { filterKeys.forEach { self[it] = 1 } })
                }
            }
        }

        private fun Query<CaseAttr, *>.toMongoSort(): Document {
            val sortOrders =
                if (sortOrders.isNotEmpty())
                    sortOrders else
                    listOf(QuerySortOrder("date", SortDirection.ASCENDING))
            return doc {
                sortOrders.forEach { sortOrder: QuerySortOrder ->
                    CASE_ATTR_META_DATA_MAP[sortOrder.sorted]?.view?.ifSortable { sortKey ->
                        self[sortKey] = when (sortOrder.direction) {
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