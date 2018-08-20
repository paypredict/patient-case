package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.worklist.*
import org.bson.Document
import kotlin.properties.Delegates

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class CaseIssuesForm : Composite<Div>() {
    var value: CaseStatus? by Delegates.observable(null) { _, _: CaseStatus?, new: CaseStatus? ->
        accession.value = new?.accession ?: ""
        claim.value = new?.claim ?: ""

        val caseIssues = new?.let {
            DBS.Collections.casesIssues().find(Document("_id", it._id)).firstOrNull()?.toCaseIssues()
        }
        issuesNPI.value = caseIssues?.npi
        issuesEligibility.value = caseIssues?.eligibility
        issuesAddress.value = caseIssues?.address
    }

    private val accession = TextField("Accession").apply {
        isReadOnly = true
    }
    private val claim = TextField("Claim ID").apply {
        isReadOnly = true
    }

    private val issuesNPI = IssuesForm(IssueNPI)
    private val issuesEligibility = IssuesForm(IssueEligibility)
    private val issuesAddress = IssuesForm(IssueAddress)

    init {
        content.setSizeFull()
        content.style["overflow"] = "auto"
        content += VerticalLayout().apply {
            setSizeUndefined()
            this += HorizontalLayout().apply {
                defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
                this += Span("Issues:").apply { style["font-weight"] = "bold" }
                this += accession
                this += claim
            }
            this  += VerticalLayout(issuesNPI, issuesEligibility, issuesAddress).apply {
                isPadding = false
                height = null
            }
        }
    }
}