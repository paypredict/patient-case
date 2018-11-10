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
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityCheckRes
import org.bson.Document
import kotlin.properties.Delegates


/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
class CaseIssuesForm : Composite<Div>() {
    var value: CaseAttr?
            by Delegates.observable(null) { _, _: CaseAttr?, new: CaseAttr? ->
                update(new)
            }
    var onValueChange: ((CaseAttr?) -> Unit)? = null
    var onCasesUpdated: (() -> Unit)? = null
    var onResolved: ((CaseAttr) -> Unit)? = null

    private fun update(new: CaseAttr?) {
        accession.value = new?.accession ?: ""
        payerName.value = new?.payerName ?: ""

        val caseHist = new?.let {
            DBS.Collections.cases().find(it._id._id()).firstOrNull()?.toCaseHist()
        }
        val patient = caseHist?.patient
        patientFirstName.value = patient?.firstName ?: ""
        patientLastName.value = patient?.lastName ?: ""
        patientMI.value = patient?.mi ?: ""
        patientDOB.value = patient?.dobAsLocalDate

        issuesNPI.value = caseHist?.npi
        issuesEligibility.value = caseHist?.eligibility
        issuesAddress.value = caseHist?.address
        issuesExpert.value = caseHist?.expert

        issueActions.isVisible = new != null
        issueResolved.value = new?.status?.resolved == true
        issueResolved.isEnabled = new?.status?.isCheckedOnly == true

        requisitionFormsNotFound.isVisible = new?.accession?.let { accession ->
            DBS.Collections
                .requisitionForms()
                .count(doc { self["barcode"] = accession }) == 0L
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
                value?.let { caseAttr ->
                    if (caseAttr.isCheckedOnly) {
                        if (event.value) {
                            confirmResolved(
                                confirmed = {
                                    caseAttr.resolve()
                                    checkbox.isEnabled = false
                                    onResolved?.invoke(caseAttr)
                                },
                                canceled = {
                                    checkbox.value = false
                                }
                            )
                        } else {
                            checkbox.value = false
                        }
                    } else {
                        checkbox.value = false
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

    private fun CaseAttr.openEligibilityDialog(selected: IssueEligibility) {
        Dialog().also { dialog ->
            dialog.width = "90vw"
            dialog.height = "90vh"
            dialog += PatientEligibilityForm(readOnly = status?.value != "CHECKED").also { form ->
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
                            addEligibilityIssue(
                                issue,
                                IssueEligibility.Status.Confirmed,
                                "onPatientEligibilityChecked: Confirmed"
                            )
                        }
                        is EligibilityCheckRes.Warn -> {
                            res.fixAddress()
                            addEligibilityIssue(
                                issue,
                                IssueEligibility.Status.Problem(
                                    "Problem With Eligibility",
                                    res.warnings.joinToString { it.message }),
                                "onPatientEligibilityChecked: Problem"
                            )
                        }
                        EligibilityCheckRes.NotAvailable -> {
                            addEligibilityIssue(
                                issue,
                                IssueEligibility.Status.NotAvailable,
                                "onPatientEligibilityChecked: NotAvailable"
                            )
                        }
                        is EligibilityCheckRes.Error -> {
                            addEligibilityIssue(
                                issue,
                                IssueEligibility.Status.Problem("Checking Error", res.message),
                                "onPatientEligibilityChecked: Problem"
                            )
                            showError(res.message)
                        }
                    }
                    onValueChange?.invoke(this)
                    form.items = issuesEligibility.value
                }
                form.onPatientEligibilitySave = { issue ->
                    addEligibilityIssue(issue, issue.status, "onPatientEligibilitySave")
                    onValueChange?.invoke(this)
                    form.items = issuesEligibility.value
                }
                form.onInsert = { responsibility ->
                    addEligibilityIssue(responsibility, message = "onInsert")
                    form.items = issuesEligibility.value
                }
                form.onRemove = { responsibility ->
                    removeEligibilityIssue(responsibility, "onRemove")
                    form.items = issuesEligibility.value
                }
            }
            dialog.isCloseOnOutsideClick = false
            dialog.open()
        }
    }

    private fun IssueAddress.formatStatus(): String =
        status?.run { "$name ${footnotes ?: ""}".trim() } ?: "invalid status"

    private fun EligibilityCheckRes.HasResult.fixAddress() {
        val caseStatus = value ?: return
        val caseId = caseStatus._id

        fun addAddress() {
            findSubscriberAddress()
                ?.also { issueAddress ->
                    caseStatus.addAddressIssue(
                        issueAddress.copy(status = IssueAddress.Status.Unchecked()),
                        message = "fixAddress: Unchecked"
                    )
                    IssueChecker().checkIssueAddressAndUpdateStatus(issueAddress)
                    caseStatus.addAddressIssue(
                        issueAddress,
                        message = "fixAddress: " + issueAddress.formatStatus()
                    )
                }
        }

        val cases = DBS.Collections.cases().find(caseId._id()).firstOrNull()?.toCaseHist()
            ?: return
        val address = cases.address.lastOrNull()
            ?: return addAddress()
        if (address.status?.passed != true)
            addAddress()
    }

    private fun CaseAttr.addEligibilityIssue(
        issue: IssueEligibility,
        statusValue: IssueEligibility.Status? = null,
        message: String
    ) {
        val cases = DBS.Collections.cases()
        val byId = Document("_id", _id)
        val caseHist: CaseHist = cases.find(byId).first().toCaseHist()
        val new = issue.copy(status = statusValue)
        caseHist.eligibility += new
        caseHist.update(
            UpdateContext(
                source = ".user",
                action = "hist.eligibility.add",
                message = message
            )
        )
        issuesEligibility.value = caseHist.eligibility
        value?.eligibility = new.status
    }

    private fun CaseAttr.removeEligibilityIssue(
        predicate: (IssueEligibility) -> Boolean,
        message: String
    ) {
        val cases = DBS.Collections.cases()
        val byId = Document("_id", _id)
        val caseHist: CaseHist = cases.find(byId).first().toCaseHist()
        caseHist.eligibility = caseHist.eligibility.filterNot(predicate)
        caseHist.update(
            UpdateContext(
                source = ".user",
                action = "hist.eligibility.remove",
                message = message
            )
        )
        issuesEligibility.value = caseHist.eligibility
    }

    private fun CaseAttr.openAddressDialog(address: IssueAddress) {
        Dialog().also { dialog ->
            dialog.width = "90vw"
            dialog.height = "90vh"
            dialog += AddressForm(readOnly = status?.value != "CHECKED").apply {
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
            IssueChecker()
                .checkIssueAddressAndUpdateStatus(issueCopy)
            value = issueCopy
            this@CaseIssuesForm.value?.addAddressIssue(
                issueCopy,
                issueCopy.status,
                "checkAddress: " + issueCopy.formatStatus()
            )
        } catch (e: CheckingException) {
            showError(e.message)
            this@CaseIssuesForm.value?.addAddressIssue(
                issueCopy,
                IssueAddress.Status.Error("Checking Error", e.message),
                "checkAddress: Error (" + issueCopy.formatStatus() + ")"
            )
        }
    }

    private fun CaseAttr.addAddressIssue(
        issue: IssueAddress,
        statusValue: IssueAddress.Status? = issue.status,
        message: String
    ) {
        val cases = DBS.Collections.cases()
        val byId = Document("_id", _id)
        val caseHist: CaseHist = cases.find(byId).first().toCaseHist()
        caseHist.address += issue.copy(status = statusValue)
        caseHist.update(
            UpdateContext(
                source = ".user",
                action = "hist.address.add",
                message = message
            )
        )
        issuesAddress.value = caseHist.address
        value?.address = statusValue
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