package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.Text
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.data.provider.DataProvider
import com.vaadin.flow.data.renderer.TemplateRenderer
import net.paypredict.patient.cases.data.worklist.*
import org.intellij.lang.annotations.Language

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class IssuesFormGrid<T : IssuesStatus>(private val issuesClass: IssuesClass<T>) : Composite<VerticalLayout>() {

    private class IssuesLayout<T : IssuesStatus>(
        issuesClass: IssuesClass<T>,
        items: List<T>
    ) : VerticalLayout() {
        val grid: Grid<T> = Grid<T>().apply {
            height = null
            isHeightByRows = true
            issuesClass.metaData.values
                .sortedBy { it.view.order }
                .forEach { metaData ->
                    val name = metaData.prop.name
                    val itemName = "item.$name"
                    @Language("HTML")
                    val template =
                        when (metaData.prop) {
                            IssueEligibility::insurance -> """
                                <div class='overflow-ellipsis'>
                                    <span title="Type">[[$itemName.${Insurance::typeCode.name}]]</span>
                                    <span title="Payer ID">[[$itemName.${Insurance::payerId.name}]]</span>
                                    <span title="Plan Code">[[$itemName.${Insurance::planCode.name}]]</span>
                                </div>
                                <div class='overflow-ellipsis'>
                                    <span title="Payer Name">[[$itemName.${Insurance::payerName.name}]]</span>
                                </div>
                                """
                            IssueEligibility::subscriber -> """
                                <div class='overflow-ellipsis'>
                                    <span title="First Name">[[$itemName.${Subscriber::firstName.name}]]</span>
                                    <span title="Last Name">[[$itemName.${Subscriber::lastName.name}]]</span>
                                    <span title="MI">[[$itemName.${Subscriber::mi.name}]]</span>
                                </div>
                                <div class='overflow-ellipsis'>
                                    <span title="Group Name">[[$itemName.${Subscriber::groupName.name}]]</span>
                                    <span title="Group ID">[[$itemName.${Subscriber::groupId.name}]]</span>
                                </div>
                                <div class='overflow-ellipsis'>
                                    <span title="Policy Number">[[$itemName.${Subscriber::policyNumber.name}]]</span>
                                </div>
                                """
                            else -> """
                                <div class='overflow-ellipsis'>[[$itemName]]</div>
                                """
                        }
                    val renderer = TemplateRenderer
                        .of<T>(template)
                        .withProperty(name) { metaData.prop.get(it) }
                    addColumn(renderer).apply {
                        setHeader(metaData.view.caption)
                        flexGrow = metaData.view.flexGrow
                    }
                }
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