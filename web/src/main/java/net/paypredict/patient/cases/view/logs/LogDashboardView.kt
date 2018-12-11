package net.paypredict.patient.cases.view.logs

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.splitlayout.SplitLayout
import net.paypredict.patient.cases.view.plusAssign

class LogDashboardView : Composite<SplitLayout>() {
    private val grid = LogSumGrid().apply {
        height = "100%"
        element.style["border-left"] = "none"
        element.style["border-right"] = "none"
    }

    init {
        val layoutLeft: VerticalLayout =
            VerticalLayout()
                .apply {
                    isPadding = false
                    setSizeFull()
                    this += grid
                }

        with(content) {
            setSizeFull()
            orientation = SplitLayout.Orientation.HORIZONTAL
            addToPrimary(layoutLeft)
        }
    }
}
