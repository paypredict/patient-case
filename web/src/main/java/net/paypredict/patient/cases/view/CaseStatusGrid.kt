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
import com.vaadin.flow.data.selection.SelectionEvent
import com.vaadin.flow.shared.Registration
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.worklist.*
import org.bson.Document
import org.bson.conversions.Bson
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.set
import kotlin.collections.sortedBy
import kotlin.collections.toList
import kotlin.collections.toTypedArray
import kotlin.reflect.jvm.javaType

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class CaseStatusGrid : Composite<Grid<CaseStatus>>() {
    override fun initContent(): Grid<CaseStatus> =
        Grid(CaseStatus::class.java)

    init {
        content.setColumns(*CASE_STATUS_META_DATA_MAP.entries.sortedBy { it.value.view.order }.map { it.key }.toTypedArray())
        for (column in content.columns) {
            val meta = CASE_STATUS_META_DATA_MAP[column.key] ?: continue
            column.isVisible = meta.view.isVisible
            if (Status::class.javaObjectType == meta.prop.returnType.javaType) {
                column.isVisible = false
                content.addColumn(
                    IconRenderer(
                        {
                            when ((meta.prop.get(it) as? Status)?.value) {
                                "AUTO_FIXED" -> Icon(VaadinIcon.WARNING).apply { color = "gold" }
                                "ERROR" -> Icon(VaadinIcon.EXCLAMATION_CIRCLE).apply { color = "red" }
                                else -> Icon(VaadinIcon.CHECK_CIRCLE).apply { color = "lightgreen" }
                            }
                        },
                        { "" })
                ).apply {
                    setHeader(meta.view.caption)
                }
            }
        }
        content.dataProvider = DataProvider.fromFilteringCallbacks(
            { query: Query<CaseStatus, Unit> ->
                collection()
                    .find()
                    .sort(query.toMongoSort())
                    .skip(query.offset)
                    .limit(query.limit)
                    .map { it.toCaseStatus() }
                    .toList()
                    .stream()
            },
            { collection().count().toInt() }
        )

    }

    fun addSelectionListener(listener: (SelectionEvent<Grid<CaseStatus>, CaseStatus>) -> Unit): Registration =
        content.addSelectionListener(listener)


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
}

private fun collection() = DBS.Collections.casesRaw()

private fun Query<CaseStatus, *>.toMongoSort(): Bson? {
    if (sortOrders.isEmpty()) return null
    return Document().also { document ->
        sortOrders.forEach { sortOrder: QuerySortOrder ->
            document[sortOrder.sorted] = when (sortOrder.direction) {
                null,
                SortDirection.ASCENDING -> 1
                SortDirection.DESCENDING -> -1
            }
        }
    }
}
