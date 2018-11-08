package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.checkbox.Checkbox
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
        filter(viewOnlyUnresolved = true)
    }
    private val form = CaseIssuesForm().apply {
        onValueChange = { caseStatus -> if (caseStatus != null) grid.refreshItem(caseStatus) }
        onCasesUpdated = { grid.refresh() }
        onResolved = { grid.refresh() }
    }

    private val viewOnlyUnresolved = Checkbox("View only unresolved issues", true).apply {
        addValueChangeListener {
            grid.filter(viewOnlyUnresolved = value)
        }
    }

    init {
        grid.addSelectionListener { event ->
            form.value = if (event.allSelectedItems.size == 1) event.allSelectedItems.firstOrNull() else null
        }

        val layoutLeft = VerticalLayout().apply {
            isPadding = false
            setSizeFull()
            this += HorizontalLayout().apply {
                isPadding = true
                defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
                width = "100%"
                this += viewOnlyUnresolved
                this += Button("Archive All")
                this += Button("History")
                this += Button("Subscribe")
                this += Button("Filter", VaadinIcon.SEARCH.create())
            }
            this += grid
        }

        with(content) {
            setSizeFull()
            orientation = SplitLayout.Orientation.HORIZONTAL
            addToPrimary(layoutLeft)
            addToSecondary(form)
        }
    }
}


