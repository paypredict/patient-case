package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.splitlayout.SplitLayout

class WorkListView : Composite<SplitLayout>() {
    private val grid = CaseAttrGrid().apply {
        height = "100%"
        element.style["border-left"] = "none"
        element.style["border-right"] = "none"
        filter(viewOnlyUnsent = false)
    }

    private val form = CaseIssuesForm().apply {
        onValueChange = { caseStatus -> if (caseStatus != null) grid.refreshItem(caseStatus) }
        onCasesUpdated = { grid.refresh() }
        onResolved = { grid.refresh() }
    }

    private val viewOnlyUnsent: Checkbox =
        Checkbox("Unsent Issues Only", false)
            .apply {
                addValueChangeListener {
                    grid.filter(viewOnlyUnsent = value)
                }
            }

    private val defaultHeader: HorizontalLayout =
        HorizontalLayout()
            .apply {
                isPadding = true
                defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
                width = "100%"
                this += Button("Search", VaadinIcon.SEARCH.create()) {
                    showSearchDialog()
                }
                this += viewOnlyUnsent
            }


    private var searchParameters: SearchParameters? = null
    private val searchResultLabel = Span().apply { element.style["font-weight"] = "500" }
    private val searchResultHeader: HorizontalLayout =
        HorizontalLayout()
            .apply {
                isPadding = true
                width = "100%"
                this += VaadinIcon.SEARCH.create()
                this += searchResultLabel
                this += HorizontalLayout().apply {
                    isPadding = false
                    this += Button("Change") { showSearchDialog(searchParameters) }
                    this += Button("Clear") { cancelSearch() }
                }
                setFlexGrow(1.0, searchResultLabel)
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

    private fun showSearchResult(result: SearchResult) {
        searchParameters = result.parameters
        searchResultLabel.text = "Search Result: " + result.size
        header.replaceContent(searchResultHeader)
        grid.filter(newFilter = result.filter)
    }

    private fun cancelSearch() {
        header.replaceContent(defaultHeader)
        grid.filter(viewOnlyUnsent = viewOnlyUnsent.value)
    }

    private fun showSearchDialog(searchParameters: SearchParameters? = null) =
        Dialog()
            .also { dialog ->
                dialog += CaseSearchForm(
                    searchParameters = searchParameters,
                    onCancel = {
                        dialog.close()
                    },
                    onFound = {
                        showSearchResult(it)
                        dialog.close()
                    }
                )
            }
            .open()

}


