package net.paypredict.patient.cases.view.logs

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.dependency.HtmlImport
import com.vaadin.flow.component.page.Push
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route

@HtmlImport("styles/shared-styles.html")
@Route("portal/log-dashboard")
@Push
@PageTitle("Log Dashboard")
class LogDashboardPage : Composite<LogDashboardView>()