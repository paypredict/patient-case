package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.data.provider.DataProvider
import com.vaadin.flow.data.provider.Query
import com.vaadin.flow.data.provider.QuerySortOrder
import com.vaadin.flow.data.provider.SortDirection
import com.vaadin.flow.data.renderer.ComponentRenderer
import com.vaadin.flow.function.SerializableFunction
import net.paypredict.patient.cases.data.Case
import net.paypredict.patient.cases.data.CasesCollection
import net.paypredict.patient.cases.data.caseDataViewMap
import net.paypredict.patient.cases.data.toCase
import org.bson.Document
import org.bson.conversions.Bson
import kotlin.reflect.KProperty1

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class CaseGrid(
    visibleProperties: Set<KProperty1<Case, *>> = setOf(
        Case::ptnLast,
        Case::ptnFirst,
        Case::prn
    ),
    sortableProperties: Set<KProperty1<Case, *>> = setOf(
        Case::ptnLast,
        Case::ptnFirst,
        Case::prn
    )
) : Composite<Grid<Case>>() {
    override fun initContent(): Grid<Case> =
        Grid(Case::class.java)

    init {
        val visibleColumns = visibleProperties.map { it.name }.toSet()
        val sortableColumns = sortableProperties.map { it.name }.toSet()

        content.isMultiSort = true
        for (column in content.columns) {
            column.isVisible = column.key in visibleColumns
            column.isSortable = column.key in sortableColumns
            val dataView = caseDataViewMap[column.key] ?: continue
            column.setHeader(Span(dataView.caption).apply {
                element.setAttribute("title", dataView.description)
            })
        }

        content.isDetailsVisibleOnClick = true
        content.setItemDetailsRenderer(ComponentRenderer(SerializableFunction { case: Case ->
            CaseDetails().apply {
                this.case = case
            }
        }))

        content.dataProvider = DataProvider.fromFilteringCallbacks(
            { query: Query<Case, Unit> ->
                CasesCollection.collection()
                    .find()
                    .sort(query.toMongoSort())
                    .skip(query.offset)
                    .limit(query.limit)
                    .map { it.toCase() }
                    .toList()
                    .stream()
            },
            { CasesCollection.collection().count().toInt() }
        )
    }
}

private fun Query<Case, *>.toMongoSort(): Bson? {
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
