package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.splitlayout.SplitLayout

class WorkListView : Composite<SplitLayout>() {
    private val grid = CaseStatusGrid().apply {
        height = "100%"
        filter(viewOnlyUnsolved = true)
    }
    private val form = CaseIssuesForm().apply {
        onValueChange = { caseStatus -> if (caseStatus != null) grid.refreshItem(caseStatus) }
        onSolved = { _, statusValue ->
            when {
                statusValue == "SOLVED" && viewOnlyUnsolved.value -> grid.refresh()
            }
        }
    }

    private val viewOnlyUnsolved = Checkbox("View only unsolved issues", true).apply {
        addValueChangeListener {
            grid.filter(viewOnlyUnsolved = value)
        }
    }

    init {
        grid.addSelectionListener { event ->
            form.value = if (event.allSelectedItems.size == 1) event.allSelectedItems.firstOrNull() else null
        }

        val layoutLeft = VerticalLayout().apply {
            setSizeFull()
            this += HorizontalLayout().apply {
                defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
                width = "100%"
                this += viewOnlyUnsolved
                this += Button("Archive All")
                this += Button("History")
                this += Button("Subscribe")
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


