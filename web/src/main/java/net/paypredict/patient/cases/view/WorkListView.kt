package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.splitlayout.SplitLayout

class WorkListView : Composite<SplitLayout>() {
    init {
        val caseStatusGrid = CaseStatusGrid().apply {
            height = "100%"
        }
        val caseStatusForm = CaseIssuesForm().apply {
            caseStatusGrid.addSelectionListener {
                value = if (it.allSelectedItems.size == 1) it.allSelectedItems.firstOrNull() else null
            }
            onValueChange = {
                value?.let { caseStatusGrid.refreshItem(it) }
            }
        }
        val caseStatusListPanel = VerticalLayout().apply {
            setSizeFull()

            this += HorizontalLayout().apply {
                defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
                width = "100%"
                this += Checkbox("Show only not fixed items", true).apply { isReadOnly = true }
                this += Button("Archive All")
                this += Button("History")
                this += Button("Subscribe")
            }
            this += caseStatusGrid
        }

        with(content) {
            setSizeFull()
            orientation = SplitLayout.Orientation.HORIZONTAL
            addToPrimary(caseStatusListPanel)
            addToSecondary(caseStatusForm)
        }
    }
}


