package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.splitlayout.SplitLayout
import org.bson.Document

class WorkListView : Composite<SplitLayout>() {
    private val grid = CaseAttrGrid().apply {
        height = "100%"
        element.style["border-left"] = "none"
        element.style["border-right"] = "none"
        filter(viewOnlyUnresolved = true)
    }

    private val form = CaseIssuesForm().apply {
        onValueChange = { caseStatus -> if (caseStatus != null) grid.refreshItem(caseStatus) }
        onCasesUpdated = { grid.refresh() }
        onResolved = { grid.refresh() }
    }

    private val viewOnlyUnresolved: Checkbox =
        Checkbox("View only unresolved issues", true)
            .apply {
                addValueChangeListener {
                    grid.filter(viewOnlyUnresolved = value)
                }
            }

    private val defaultHeader: HorizontalLayout =
        HorizontalLayout()
            .apply {
                isPadding = true
                defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
                width = "100%"
                this += viewOnlyUnresolved
                this += Button("Search", VaadinIcon.SEARCH.create()) {
                    showSearchDialog()
                }
            }

    private val searchResultHeader: HorizontalLayout =
        HorizontalLayout()
            .apply {
                isPadding = true
                defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
                width = "100%"
                this += Button("Search", VaadinIcon.SEARCH.create()) { showSearchDialog() }
                    .apply { width = "100%" }
                this += Button("Clear") { cancelSearch() }
            }

    private val header: HorizontalLayout =
        HorizontalLayout()
            .apply {
                isPadding = false
                width = "100%"
                this += defaultHeader
            }

    init {
        grid.addSelectionListener { event ->
            form.value = if (event.allSelectedItems.size == 1) event.allSelectedItems.firstOrNull() else null
        }

        val layoutLeft: VerticalLayout =
            VerticalLayout()
                .apply {
                    isPadding = false
                    setSizeFull()
                    this += header
                    this += grid
                }

        with(content) {
            setSizeFull()
            orientation = SplitLayout.Orientation.HORIZONTAL
            addToPrimary(layoutLeft)
            addToSecondary(form)
        }
    }

    private fun HorizontalLayout.replaceContent(newContent: Component) {
        removeAll()
        this += newContent
    }

    private fun showSearchResult(filter: Document) {
        header.replaceContent(searchResultHeader)
        grid.filter(newFilter = filter)
    }

    private fun cancelSearch() {
        header.replaceContent(defaultHeader)
        grid.filter(viewOnlyUnresolved = viewOnlyUnresolved.value)
    }

    private fun showSearchDialog() =
        Dialog()
            .also { dialog ->
                dialog += CaseSearchForm(
                    onCancel = {
                        dialog.close()
                    },
                    onFound = { filter ->
                        showSearchResult(filter)
                        dialog.close()
                    }
                )
            }
            .open()

}


