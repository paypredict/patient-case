package net.paypredict.patient.cases.view

import com.pipl.api.data.containers.Person
import com.pipl.api.data.fields.*
import com.pipl.api.search.SearchAPIError
import com.pipl.api.search.SearchAPIRequest
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.H4
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import net.paypredict.patient.cases.apis.pipl.piplApiSearchConfiguration
import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.data.worklist.*
import net.paypredict.patient.cases.mongo.`$set`
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityCheckRes
import org.bson.Document
import java.time.ZoneOffset
import java.util.*
import kotlin.properties.Delegates


/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class CaseIssuesForm : Composite<Div>() {
    var value: CaseStatus?
            by Delegates.observable(null) { _, _: CaseStatus?, new: CaseStatus? ->
                update(new)
            }
    var onValueChange: ((CaseStatus?) -> Unit)? = null
    var onSolved: ((CaseStatus, statusValue: String?) -> Unit)? = null

    private fun update(new: CaseStatus?) {
        accession.value = new?.accession ?: ""
        payerName.value = new?.payerName ?: ""

        val caseIssues = new?.let {
            DBS.Collections.casesIssues().find(Document("_id", it._id)).firstOrNull()?.toCaseIssue()
        }
        val patient = caseIssues?.patient
        patientFirstName.value = patient?.firstName ?: ""
        patientLastName.value = patient?.lastName ?: ""
        patientMI.value = patient?.mi ?: ""
        patientDOB.value = patient?.dobAsLocalDate

        issuesNPI.value = caseIssues?.npi
        issuesEligibility.value = caseIssues?.eligibility
        issuesAddress.value = caseIssues?.address
        issuesExpert.value = caseIssues?.expert

        issueActions.isVisible = new != null
        issueSolved.value = new?.statusValue == "SOLVED"
    }

    private val payerName = TextField("Payer Name").apply { isReadOnly = true }
    private val accession = TextField("Accession").apply { isReadOnly = true }

    private val patientFirstName = TextField("Patient First Name").apply { isReadOnly = true }
    private val patientLastName = TextField("Patient Last Name").apply { isReadOnly = true }
    private val patientMI = TextField("Patient MI").apply { isReadOnly = true }
    private val patientDOB = DatePicker("Patient DOB").apply { isReadOnly = true }

    private val issuesNPI = IssuesFormGrid(IssueNPI)
    private val issuesEligibility = IssuesFormGrid(IssueEligibility) {
        value?.openEligibilityDialog(it)
    }
    private val issuesAddress = IssuesFormGrid(IssueAddress) { openAddressDialog(it) }
    private val issuesExpert = IssuesFormNote(IssueExpert)

    private val issueSolved = Checkbox("Issue Solved").also { checkbox ->
        checkbox.addValueChangeListener { event ->
            if (event.isFromClient) {
                value?.let { caseStatus ->
                    val statusValue = when (event.value) {
                        true -> "SOLVED"
                        false -> null
                    }
                    caseStatus.statusValue = statusValue
                    onSolved?.invoke(caseStatus, statusValue)
                }
            }
        }
    }

    private val issueActions = HorizontalLayout(issueSolved).apply {
        isVisible = false
        isPadding = false
    }

    init {
        content.setSizeFull()
        content.style["overflow"] = "auto"
        content += VerticalLayout().apply {
            setSizeUndefined()
            this += FormLayout().apply {
                setResponsiveSteps(
                    FormLayout.ResponsiveStep("8em", 1),
                    FormLayout.ResponsiveStep("16em", 2),
                    FormLayout.ResponsiveStep("24em", 3),
                    FormLayout.ResponsiveStep("32em", 4)
                )

                this += H3("Case Issues").apply {
                    style["margin-top"] = "0"
                    style["white-space"] = "nowrap"
                }
                this += payerName.apply { element.setAttribute("colspan", "2") }
                this += accession

                this += patientLastName
                this += patientFirstName
                this += patientMI
                this += patientDOB

            }
            this += VerticalLayout(issuesNPI, issuesEligibility, issuesAddress, issuesExpert, issueActions).apply {
                isPadding = false
                height = null
                setHorizontalComponentAlignment(FlexComponent.Alignment.END, issueActions)
            }
        }
    }

    private fun CaseStatus.openEligibilityDialog(eligibility: IssueEligibility) {
        Dialog().also { dialog ->
            dialog.width = "90vw"
            dialog.height = "90vh"
            dialog += PatientEligibilityForm().also { form ->
                form.isPadding = false
                form.width = "100%"
                form.height = "100%"
                form.caseId = _id
                form.value = eligibility
                form.onCancel = { dialog.close() }
                form.onPatientEligibilityChecked = { issue, res ->
                    when (res) {
                        is EligibilityCheckRes.Pass -> {
                            addEligibilityIssue(issue, "PASS")
                        }
                        is EligibilityCheckRes.Warn -> {
                            addEligibilityIssue(issue, "WARNING")
                            showWarnings(res.warnings)
                        }
                        is EligibilityCheckRes.Error -> {
                            addEligibilityIssue(issue, "ERROR")
                            showError(res.message)
                        }
                    }
                    onValueChange?.invoke(value)
                }
                form.onPatientEligibilitySave = { issue ->
                    addEligibilityIssue(issue, "SAVED")
                    onValueChange?.invoke(value)
                    dialog.close()
                }
            }
            dialog.isCloseOnOutsideClick = false
            dialog.open()
        }
    }


    private fun CaseStatus.addEligibilityIssue(issue: IssueEligibility, statusValue: String) {
        val casesIssuesCollection = DBS.Collections.casesIssues()
        val byId = Document("_id", _id)
        val caseIssues = casesIssuesCollection.find(byId).first().toCaseIssue()
        caseIssues.eligibility += issue.copy(status = statusValue)
        casesIssuesCollection.replaceOne(byId, caseIssues.toDocument())
        val status = Status(value = statusValue)
        DBS.Collections.casesRaw().updateOne(byId,
            doc {
                doc[`$set`] = doc {
                    doc["status.values.eligibility"] = status.toDocument()
                }
            })
        issuesEligibility.value = caseIssues.eligibility
        value?.eligibility = status
    }


    private fun openAddressDialog(address: IssueAddress) {
        Dialog().apply {
            width = "70vw"
            this += AddressForm().apply {
                setSizeFull()
                isPadding = false
                value = address
                checkPatientAddress = { issue: IssueAddress ->
                    try {
                        checkAddress(issue)
                        close()
                    } catch (e: Throwable) {
                        showError(
                            when (e) {
                                is SearchAPIError -> {
                                    System.err.println(e.json)
                                    e.error
                                }
                                else -> e.message
                            }
                        )
                    }
                }
            }
            open()
        }
    }

    private fun checkAddress(issue: IssueAddress) {
        val apiRequest = SearchAPIRequest(Person(
            mutableListOf<Field>().also { fields ->
                fields += Address.Builder()
                    .country("US")
                    .state(issue.state)
                    .city(issue.city)
                    .zipCode(issue.zip)
                    .street(issue.address1)
                    .build()
                issue.person?.also { person ->
                    fields += Name.Builder()
                        .apply {
                            person.firstName?.also { first(it) }
                            person.lastName?.also { last(it) }
                            person.mi?.also { middle(it) }
                        }
                        .build()
                    person.dobAsLocalDate?.also { dob ->
                        fields += DOB(DateRange().apply {
                            start = Date.from(
                                dob
                                    .withMonth(1)
                                    .withDayOfMonth(1)
                                    .atStartOfDay(ZoneOffset.UTC)
                                    .toInstant()
                            )
                            end = Date.from(
                                dob
                                    .withMonth(12)
                                    .withDayOfMonth(31)
                                    .atStartOfDay(ZoneOffset.UTC)
                                    .toInstant()
                            )
                        })
                    }
                }
            }
        ),
            piplApiSearchConfiguration
        )
        val response = apiRequest.send()
        println(response.json)
    }

    private fun showError(error: String?) {
        Dialog().apply {
            this += VerticalLayout().apply {
                this += H2("API Call Error")
                this += H3(error)
            }
            open()
        }
    }

    private fun showWarnings(warnings: List<EligibilityCheckRes.Warning>) {
        Dialog().apply {
            this += VerticalLayout().apply {
                this += H2("WARNING")
                warnings.forEach {
                    this += H4(it.message)
                }
            }
            open()
        }
    }
}