package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.dependency.HtmlImport
import com.vaadin.flow.component.page.Push
import com.vaadin.flow.router.BeforeEnterEvent
import com.vaadin.flow.router.BeforeEnterObserver
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route

/**
 * The main view contains a button and a template element.
 */
@HtmlImport("styles/shared-styles.html")
@Route("")
@Push
@PageTitle("WorkList")
class MainView : Composite<WorkListView>(), BeforeEnterObserver {
    override fun beforeEnter(event: BeforeEnterEvent) =
        checkUserOnBeforeEnterEvent<MainView>(event)
}
