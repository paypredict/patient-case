package net.paypredict.patient.cases.view

import com.vaadin.flow.component.dependency.HtmlImport
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.Route

/**
 * The main view contains a button and a template element.
 */
@HtmlImport("styles/shared-styles.html")
@Route("case")
class CaseView : VerticalLayout() {
    init {
        className = "main-layout"
        this += CaseGrid().apply {
            height = "100%"
        }
    }
}
