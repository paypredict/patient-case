package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.Text
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.data.provider.DataProvider
import com.vaadin.flow.data.renderer.TemplateRenderer
import net.paypredict.patient.cases.data.worklist.*
import org.intellij.lang.annotations.Language

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class IssuesFormGrid<T : IssueItem>(
    private val issuesClass: IssuesClass<T>,
    private val onClickViewForm: ((T) -> Unit)? = null
) : Composite<VerticalLayout>() {

    private class IssuesLayout<T : IssueItem>(
        issuesClass: IssuesClass<T>,
        items: List<T>,
        onClickViewForm: ((T) -> Unit)? = null
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
                            IssueNPI::status,
                            IssueEligibility::status,
                            IssueAddress::status,
                            IssueExpert::status -> """
                                <div class='overflow-ellipsis'>
                                    <span>[[$itemName.name]]</span>
                                </div>
                                """
                            IssueEligibility::insurance -> """
                                <div class='overflow-ellipsis'>
                                    <span title="Type">[[$itemName.${Insurance::typeCode.name}]]</span>
                                    <span title="Payer ID">[[$itemName.${Insurance::payerId.name}]]</span>
                                    <span title="Plan Code">[[$itemName.${Insurance::planCode.name}]]</span>
                                    <span style="font-weight: bold"  title="Cortex Payer ID">[[$itemName.${Insurance::zmPayerId.name}]]</span>
                                </div>
                                <div class='overflow-ellipsis'>
                                    <span title="Payer Name">[[$itemName.${Insurance::payerName.name}]]</span>
                                </div>
                                <div class='overflow-ellipsis'>
                                    <span style="font-weight: bold" title="Cortex Payer Name">[[$itemName.${Insurance::zmPayerName.name}]]</span>
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
                            IssueNPI::name -> """
                                <div class='overflow-ellipsis'>
                                    <span title="First Name">[[$itemName.${Person::firstName.name}]]</span>
                                    <span title="Last Name">[[$itemName.${Person::lastName.name}]]</span>
                                    <span title="MI">[[$itemName.${Person::mi.name}]]</span>
                                </div>
                                """
                            IssueNPI::primaryTaxonomy -> """
                                <dom-if if="{{$itemName}}">
                                    <template>
                                        <div class='overflow-ellipsis'>
                                            <span>Primary: </span>
                                            <span>[[$itemName.${IssueNPI.Taxonomy::code.name}]]</span>
                                            <span> - </span>
                                            <span>[[$itemName.${IssueNPI.Taxonomy::desc.name}]]</span>
                                        </div>
                                    </template>
                                </dom-if>
                                """
                            IssueAddress::status -> """
                                <div class='overflow-ellipsis'>[[$itemName]]</div>
                                <dom-if if="{{item.${IssueAddress::error.name}}}">
                                    <template>
                                        <div class='overflow-ellipsis'>
                                            <span title="[[item.${IssueAddress::error.name}]]" style="color: red"
                                                        >[[item.${IssueAddress::error.name}]]</span>
                                        </div>
                                    </template>
                                </dom-if>
                                """
                            IssueAddress::footNoteSet -> """<!--suppress HtmlUnknownAttribute -->
                                <template is="dom-repeat" items="{{$itemName}}">
                                    <style>
                                        .INFO { color: #1e8e3e }
                                        .WARNING { color: #f4b400 }
                                        .ERROR { color: #d23f31 }
                                    </style>
                                    <div class='overflow-ellipsis'>
                                        <span
                                            title="{{item.note}}"
                                            >[[item.label]]</span>
                                    </div>
                                </template>
                                """
                            else -> """
                                <div class='overflow-ellipsis'>[[$itemName]]</div>
                                """
                        }
                    val renderer = TemplateRenderer
                        .of<T>(template)
                        .withProperty(name) { metaData.prop.get(it) }
                    addColumn(renderer).apply {
                        setHeader(metaData.view.label)
                        flexGrow = metaData.view.flexGrow
                        isVisible = metaData.view.isVisible
                    }
                }
            if (onClickViewForm != null) {
                @Language("HTML")
                val template = """
                    <vaadin-button  on-click="showForm" theme="icon small tertiary">
                        <iron-icon icon="lumo:edit"></iron-icon>
                    </vaadin-button>
                """
                val renderer = TemplateRenderer
                    .of<T>(template)
                    .withEventHandler("showForm") {
                        select(it)
                        onClickViewForm(it)
                    }
                addColumn(renderer).apply {
                    flexGrow = 0
                    width = "55px"
                }
            }

            dataProvider = DataProvider.fromStream(items.stream())
        }

        init {
            isPadding = false
            height = null

            this += Div().apply {
                style["font-weight"] = "bold"
                this += Text(issuesClass.caption)
            }
            this += grid
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
                content.add(IssuesLayout(issuesClass, list, onClickViewForm))
            }
        }
}
