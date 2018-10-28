package net.paypredict.patient.cases.view

import com.pipl.api.search.SearchAPIError
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.server.VaadinSession
import net.paypredict.patient.cases.data.worklist.*
import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo._id
import net.paypredict.patient.cases.mongo.`$set`
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityCheckRes
import org.bson.Document
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
    var onCasesUpdated: (() -> Unit)? = null
    var onResolved: ((CaseStatus, statusValue: String?) -> Unit)? = null

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
        issueResolved.value = new?.statusValue == "RESOLVED"

        requisitionFormsNotFound.isVisible = new?.accession?.let { accession ->
            DBS.Collections
                .requisitionForms()
                .count(doc { doc["barcode"] = accession }) == 0L
        } ?: false
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
    private val issuesAddress = IssuesFormGrid(IssueAddress) {
        value?.openAddressDialog(it)
    }
    private val issuesExpert = IssuesFormNote(IssueExpert)

    private val issueResolved = Checkbox("Issue resolved").also { checkbox ->
        checkbox.addValueChangeListener { event ->
            if (event.isFromClient) {
                value?.let { caseStatus ->
                    when (caseStatus.statusValue) {
                        "RESOLVED" -> checkbox.value = true
                        else -> if (event.value) {
                            confirmResolved(
                                confirmed = {
                                    caseStatus.createOutXml()
                                    caseStatus.statusValue = "RESOLVED"
                                    onResolved?.invoke(caseStatus, "RESOLVED")
                                },
                                canceled = { checkbox.value = true }
                            )
                        }
                    }
                }
            }
        }
    }

    private val issueActions = HorizontalLayout(issueResolved).apply {
        isVisible = false
        isPadding = false
    }

    private val requisitionFormsNotFound = VaadinIcon.FILE_REMOVE.create().apply {
        color = "#d23f31"
        style["min-width"] = "1.5em"
        style["min-height"] = "1.5em"
        element.setAttribute("title", "Scanned documents not available")
        isVisible = false
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

                this += HorizontalLayout().apply {
                    isPadding = false
                    defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
                    this += H3("Case Issues").apply {
                        style["margin-top"] = "0"
                        style["white-space"] = "nowrap"
                    }
                    this += requisitionFormsNotFound
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


    private fun confirmResolved(confirmed: () -> Unit, canceled: () -> Unit) {
        val autoConfirmResolvedSessionAttr = "autoConfirmResolved"
        val onErrorHeader = "Error on make issue resolved"
        if (VaadinSession.getCurrent()?.getAttribute(autoConfirmResolvedSessionAttr) == true) {
            confirmed.uiSafeInvoke(onErrorHeader)
            return
        }
        Dialog().also { dialog ->
            dialog += VerticalLayout().apply {
                val doNotShowThisAgain = Checkbox("Do not show this again")

                val buttons = HorizontalLayout().apply {
                    this += Button("Mark issue resolved").apply {
                        element.setAttribute("theme", "error primary")
                        addClickListener {
                            if (doNotShowThisAgain.value)
                                VaadinSession.getCurrent()
                                    ?.setAttribute(autoConfirmResolvedSessionAttr, true)
                            confirmed.uiSafeInvoke(onErrorHeader)
                            dialog.close()
                        }
                    }
                    this += Button("Cancel").apply {
                        addClickListener {
                            canceled()
                            dialog.close()
                        }
                    }
                }

                this += Span(
                    """Attention! Once you mark issue resolved,
                    the order will be sent to the billing system.
                    You cannot undo this action."""
                )
                this += doNotShowThisAgain
                this += buttons

                setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, buttons)
            }
            dialog.isCloseOnOutsideClick = false
            dialog.isCloseOnEsc = false
            dialog.open()
        }
    }

    private fun (() -> Unit).uiSafeInvoke(errorHeader: String? = null) =
        try {
            invoke()
        } catch (e: Throwable) {
            e.printStackTrace()
            showError(e, errorHeader)
        }

    private fun CaseStatus.openEligibilityDialog(selected: IssueEligibility) {
        Dialog().also { dialog ->
            dialog.width = "90vw"
            dialog.height = "90vh"
            dialog += PatientEligibilityForm().also { form ->
                form.isPadding = false
                form.width = "100%"
                form.height = "100%"
                form.caseId = _id
                form.selectedItem = selected
                form.items = issuesEligibility.value
                form.onCasesUpdated = onCasesUpdated
                form.onClose = { dialog.close() }
                form.onPatientEligibilityChecked = { issue, res ->
                    @Suppress("UNUSED_VARIABLE")
                    val ignore = when (res) {
                        is EligibilityCheckRes.Pass -> {
                            res.fixAddress()
                            addEligibilityIssue(issue, IssueEligibility.Status.Confirmed)
                        }
                        is EligibilityCheckRes.Warn -> {
                            res.fixAddress()
                            addEligibilityIssue(
                                issue, IssueEligibility.Status.Problem(
                                    "Problem With Eligibility",
                                    res.warnings.joinToString { it.message }
                                )
                            )
                        }
                        EligibilityCheckRes.NotAvailable -> {
                            addEligibilityIssue(issue, IssueEligibility.Status.NotAvailable)
                        }
                        is EligibilityCheckRes.Error -> {
                            addEligibilityIssue(
                                issue, IssueEligibility.Status.Problem(
                                    "Checking Error", res.message
                                )
                            )
                            showError(res.message)
                        }
                    }
                    onValueChange?.invoke(this)
                    form.items = issuesEligibility.value
                }
                form.onPatientEligibilitySave = { issue ->
                    addEligibilityIssue(issue, issue.status)
                    onValueChange?.invoke(this)
                    form.items = issuesEligibility.value
                }
                form.onInsert = { responsibility ->
                    addEligibilityIssue(responsibility)
                    form.items = issuesEligibility.value
                }
                form.onRemove = { responsibility ->
                    removeEligibilityIssue(responsibility)
                    form.items = issuesEligibility.value
                }
            }
            dialog.isCloseOnOutsideClick = false
            dialog.open()
        }
    }

    private fun EligibilityCheckRes.HasResult.fixAddress() {
        val caseStatus = value ?: return
        val caseId = caseStatus._id

        fun addAddress() {
            findSubscriberAddress()
                ?.also { issueAddress ->
                    caseStatus.addAddressIssue(issueAddress.copy(status = IssueAddress.Status.Unchecked))
                    IssueChecker(caseId = caseId).checkIssueAddressAndUpdateStatus(issueAddress)
                    caseStatus.addAddressIssue(issueAddress)
                }
        }

        val caseIssue = DBS.Collections.casesIssues().find(caseId._id()).firstOrNull()?.toCaseIssue()
            ?: return
        val address = caseIssue.address.lastOrNull()
            ?: return addAddress()
        if (address.status?.passed != true)
            addAddress()
    }

    private fun CaseStatus.addEligibilityIssue(issue: IssueEligibility, statusValue: IssueEligibility.Status? = null) {
        val casesIssuesCollection = DBS.Collections.casesIssues()
        val byId = Document("_id", _id)
        val caseIssues = casesIssuesCollection.find(byId).first().toCaseIssue()
        val new = issue.copy(status = statusValue)
        if (new == caseIssues.eligibility.lastOrNull()) return

        var newList = caseIssues.eligibility + new
        if (newList.size > 1 && newList[0].isEmpty()) {
            newList = newList.drop(1)
        }
        caseIssues.eligibility = newList

        casesIssuesCollection.replaceOne(byId, caseIssues.toDocument())
        val status = Status(value = statusValue?.name)
        DBS.Collections.casesRaw().updateOne(byId,
            doc {
                doc[`$set`] = doc {
                    doc["status.values.eligibility"] = status.toDocument()
                }
            })
        issuesEligibility.value = caseIssues.eligibility
        value?.eligibility = status
    }

    private fun CaseStatus.removeEligibilityIssue(predicate: (IssueEligibility) -> Boolean) {
        val casesIssuesCollection = DBS.Collections.casesIssues()
        val byId = Document("_id", _id)
        val caseIssues = casesIssuesCollection.find(byId).first().toCaseIssue()
        caseIssues.eligibility = caseIssues.eligibility.filterNot(predicate)
        casesIssuesCollection.replaceOne(byId, caseIssues.toDocument())
        issuesEligibility.value = caseIssues.eligibility
    }

    private fun CaseStatus.openAddressDialog(address: IssueAddress) {
        Dialog().also { dialog ->
            dialog.width = "90vw"
            dialog.height = "90vh"
            dialog += AddressForm().apply {
                setSizeFull()
                isPadding = false
                value = address
                caseId = _id
                checkPatientAddress = { issue: IssueAddress ->
                    try {
                        checkAddress(issue)
                    } catch (e: Throwable) {
                        showError(
                            when (e) {
                                is SearchAPIError -> {
                                    System.err.println(e.json)
                                    e.error
                                }
                                else -> e.message
                            },
                            "API Call Error"
                        )
                    }
                }
                onClose = { dialog.close() }
            }
            dialog.isCloseOnOutsideClick = false
            dialog.open()
        }
    }

    private fun AddressForm.checkAddress(issue: IssueAddress) {
        val issueCopy = issue.copy()
        try {
            IssueChecker(caseId = caseId!!)
                .checkIssueAddressAndUpdateStatus(issueCopy)
            value = issueCopy
            this@CaseIssuesForm.value?.addAddressIssue(issueCopy, issueCopy.status)
        } catch (e: CheckingException) {
            showError(e.message)
            this@CaseIssuesForm.value?.addAddressIssue(
                issueCopy, (e.status as? IssuesStatusError)
                    ?.toDocument()
                    ?.toIssueAddressStatus()
                    ?: IssueAddress.Status.Error("Checking Error", e.message)
            )
        }
    }

    private fun CaseStatus.addAddressIssue(issue: IssueAddress, statusValue: IssueAddress.Status? = issue.status) {
        val casesIssuesCollection = DBS.Collections.casesIssues()
        val byId = Document("_id", _id)
        val caseIssues = casesIssuesCollection.find(byId).first().toCaseIssue()
        caseIssues.address += issue.copy(status = statusValue)
        casesIssuesCollection.replaceOne(byId, caseIssues.toDocument())
        val status = Status(value = statusValue?.name)
        DBS.Collections.casesRaw().updateOne(byId,
            doc {
                doc[`$set`] = doc {
                    doc["status.values.address"] = status.toDocument()
                }
            })
        issuesAddress.value = caseIssues.address
        value?.address = status
        onValueChange?.invoke(value)
    }

    private fun showError(x: Throwable, header: String? = "API Call Error") {
        var error = x.message
        if (error.isNullOrBlank()) error = x.javaClass.name
        showError(error, header)
    }

    private fun showError(error: String?, header: String? = "API Call Error") {
        Dialog().apply {
            this += VerticalLayout().apply {
                if (header != null)
                    this += H2(header)
                this += H3(error)
            }
            open()
        }
    }
}