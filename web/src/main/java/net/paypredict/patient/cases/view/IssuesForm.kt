package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.Text
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.data.provider.DataProvider
import net.paypredict.patient.cases.data.worklist.IssuesClass
import net.paypredict.patient.cases.data.worklist.IssuesStatus

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class IssuesForm<T : IssuesStatus>(private val issuesClass: IssuesClass<T>) : Composite<VerticalLayout>() {

    private class IssuesLayout<T : IssuesStatus>(
        issuesClass: IssuesClass<T>,
        items: List<T>
    ): VerticalLayout() {
        val grid: Grid<T> = Grid(issuesClass.beanType).apply {
            height = null
            isHeightByRows = true
            dataProvider = DataProvider.fromStream(items.stream())
        }

        val editButton = Button("Edit").apply {

        }

        init {
            isPadding = false
            height = null

            this += Div().apply {
                style["font-weight"] = "bold"
                this += Text(issuesClass.caption)
            }
            this += grid
            this += editButton
            setHorizontalComponentAlignment(FlexComponent.Alignment.END, editButton)
        }
    }

    init {
        content.height = null
        content.isPadding = false
    }

    var value: List<T>? = null
        set(value) {
            field = value
            content.removeAll()
            val list = value ?: emptyList()
            if (list.isNotEmpty()) {
                content.add(IssuesLayout(issuesClass, list))
            }
        }
}
