package net.paypredict.patient.cases.view

import com.mongodb.client.FindIterable
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.icon.Icon
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.data.provider.CallbackDataProvider
import com.vaadin.flow.data.provider.Query
import com.vaadin.flow.data.provider.QuerySortOrder
import com.vaadin.flow.data.provider.SortDirection
import com.vaadin.flow.data.renderer.IconRenderer
import com.vaadin.flow.data.renderer.LocalDateTimeRenderer
import com.vaadin.flow.data.selection.SelectionEvent
import com.vaadin.flow.data.selection.SingleSelectionEvent
import com.vaadin.flow.shared.Registration
import net.paypredict.patient.cases.*
import net.paypredict.patient.cases.data.workbook.WorkbookContext
import net.paypredict.patient.cases.data.worklist.*
import net.paypredict.patient.cases.mongo.DBS.Collections.cases
import net.paypredict.patient.cases.mongo._id
import net.paypredict.patient.cases.mongo.`$in`
import net.paypredict.patient.cases.mongo.doc
import org.apache.poi.ss.usermodel.Sheet
import org.bson.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.stream.Stream
import kotlin.collections.set
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.jvm.javaType

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class CaseAttrGrid(isEnabled: Boolean = true) : Composite<Grid<CaseAttr>>(), ThemableLayout {
    override fun initContent(): Grid<CaseAttr> =
        Grid(CaseAttr::class.java).apply {
            isEnabled = this@CaseAttrGrid.isEnabled
        }

    var isEnabled: Boolean = isEnabled
        set(new) {
            field = new
            content.isEnabled = new
        }

    private var filter: Document = doc { }

    init {

        for (columnKey in content.columns.map { it.key }) {
            content.removeColumnByKey(columnKey)
        }

        val columnKeys = CASE_ATTR_META_DATA_MAP.entries
            .asSequence()
            .filter { it.value.view.isVisible }
            .sortedBy { it.value.view.order }
            .map { it.key }
            .toList()

        for (columnKey in columnKeys) {
            val meta: MetaData<CaseAttr> = CASE_ATTR_META_DATA_MAP[columnKey] ?: continue
            val propJavaType = meta.prop.returnType.javaType
            when {
                meta.prop == CaseAttr::status -> {
                    content.addColumn(
                        IconRenderer(
                            {
                                Icon(VaadinIcon.DOT_CIRCLE).apply {
                                    val statusSum = it.status?.sum
                                    color = when (statusSum) {
                                        CaseStatus.Sum.ERROR -> "PURPLE"
                                        CaseStatus.Sum.SENT -> "GREEN"
                                        CaseStatus.Sum.HOLD -> "RED"
                                        CaseStatus.Sum.TIMEOUT -> "WHITE"
                                        CaseStatus.Sum.RESOLVED -> "GREEN"
                                        CaseStatus.Sum.PASSED -> "WHITE"
                                        CaseStatus.Sum.CHECKED -> "WHITE"
                                        null -> "WHITE"
                                    }
                                    isVisible = color != "WHITE"
                                    setSize("11px")
                                    element.setAttribute("title", "$statusSum")
                                }
                            },
                            { "" })
                    ).apply {
                        key = columnKey
                        setHeader("")
                        flexGrow = 0
                        width = "28px"
                    }
                }
                propJavaType == Date::class.javaObjectType -> {
                    content.addColumn(
                        LocalDateTimeRenderer(
                            { it.date?.toInstant()?.atZone(systemZoneId)?.toLocalDateTime() },
                            dateTimeFormat
                        )
                    ).apply {
                        key = columnKey
                        setHeader(meta.view.label)
                        if (meta.view.sortable)
                            setSortProperty(meta.prop.name)
                    }
                }
                IssuesStatus::class.java.isAssignableFrom(propJavaType as Class<*>) -> {
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
                        key = columnKey
                        setHeader(meta.view.label)
                        if (meta.view.sortable)
                            setSortProperty(meta.prop.name)
                        flexGrow = 0
                        width = "75px"
                    }
                }
                else -> {
                    content.addColumn(columnKey).apply {
                        flexGrow = meta.view.flexGrow
                        if (meta.view.sortable)
                            setSortProperty(meta.prop.name)
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
                    self["status.value"] = doc { self[`$in`] = listOf("CHECKED", "HOLD") }
                }
            else ->
                doc {
                    self["status.checked"] = true
                    self["status.timeout"] = false
                }
        }
        refresh()
    }


    private val listeners =
        mutableListOf<(SelectionEvent<Grid<CaseAttr>, CaseAttr>) -> Unit>()

    fun addSelectionListener(listener: (SelectionEvent<Grid<CaseAttr>, CaseAttr>) -> Unit): Registration {
        listeners += listener
        return content.addSelectionListener(listener)
    }


    fun refreshItem(item: CaseAttr) {
        content.dataProvider.refreshItem(item)
    }

    fun refresh(item: CaseAttr) {
        val new =
            collection()
                .find(item._id._id())
                .projection(projection)
                .first()
                .toCaseAttr()
        CASE_ATTR_META_DATA_MAP.entries.forEach {
            @Suppress("UNCHECKED_CAST")
            (it.value.prop as? KMutableProperty1<CaseAttr, Any?>)?.run {
                val value = get(new)
                set(item, value)
            }
        }
        content.dataProvider.refreshItem(item)
        val event = SingleSelectionEvent<Grid<CaseAttr>, CaseAttr>(
            content,
            content.asSingleSelect(),
            null,
            false
        )
        listeners.forEach { it(event) }
    }

    fun refresh() {
        content.dataProvider =
                if (isEnabled)
                    CallbackDataProvider<CaseAttr, Unit>(
                        { query: Query<CaseAttr, Unit> ->
                            find(filter) {
                                it.sort(query.toMongoSort())
                                    .skip(query.offset)
                                    .limit(query.limit)

                            }.stream()
                        },
                        { collection().count(filter).toInt() },
                        { source: CaseAttr? -> source?._id }
                    ) else
                    CallbackDataProvider<CaseAttr, Unit>(
                        { Stream.empty() },
                        { 0 },
                        { null }
                    )

    }

    private fun find(
        filter: Document,
        setup: (FindIterable<Document>) -> FindIterable<Document> = { it }
    ): List<CaseAttr> =
        collection()
            .find(filter)
            .projection(projection)
            .let(setup)
            .map { it.toCaseAttr() }
            .toList()

    private enum class ExportCol(val meta: MetaData<CaseAttr>) {
        STATUS(CaseAttr::status.toMetaData()!!),
        DATE(CaseAttr::date.toMetaData()!!),
        ACCESSION(CaseAttr::accession.toMetaData()!!);

        val label: String get() = meta.view.label
    }

    fun export(
        context: WorkbookContext,
        sheet: Sheet,
        filter: Document
    ) {
        val items: List<CaseAttr> = find(filter)

        sheet.createRow(0).also { row ->
            for (export in ExportCol.values()) {
                row.createCell(export.ordinal).apply {
                    cellStyle = context.headerStyle
                    setCellValue(export.label)
                }
            }
        }

        items.forEachIndexed { index, item ->
            sheet.createRow(index + 1).also { row ->
                for (export in ExportCol.values()) {
                    row.createCell(export.ordinal).apply {
                        export.meta.prop.get(item)?.also { value ->
                            when (value) {
                                is Number -> setCellValue(value.toDouble())
                                is String -> setCellValue(value)
                                is Date -> setCellValue(value).also { cellStyle = context.dateTimeStyle }
                                is CaseStatus -> setCellValue(value.value)
                                else -> setCellValue(value.toString())
                            }

                        }
                    }
                }
            }
        }

        for (export in ExportCol.values()) {
            sheet.setColumnWidth(export.ordinal, 16 * 256)
        }

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
        private val systemZoneId = ZoneId.systemDefault()

        private val dateTimeFormat: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM dd yyyy hh:mm")
        private val projection =
            doc {
                for (metaData in CASE_ATTR_META_DATA_MAP.values) {
                    metaData.view.ifHasDocKey { self[it] = 1 }
                    metaData.view.ifHasProjectionKeys { keys ->
                        keys.forEach { self[it] = 1 }
                    }
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