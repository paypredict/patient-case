package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.Text
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.tabs.Tab
import com.vaadin.flow.component.tabs.Tabs
import com.vaadin.flow.router.Route
import com.vaadin.flow.shared.Registration
import net.paypredict.patient.cases.casesUser
import net.paypredict.patient.cases.data.cases.CasesLog
import net.paypredict.patient.cases.data.cases.toCasesLog
import net.paypredict.patient.cases.data.worklist.*
import net.paypredict.patient.cases.html.ImgPanZoom
import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo._id
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.mongo.opt
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityCheckRes
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityChecker
import net.paypredict.patient.cases.pokitdok.eligibility.toEligibilityCheckRes
import net.paypredict.patient.cases.toTitleCase

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/25/2018.
 */
@Route("eligibility")
class PatientEligibilityForm(
    private val readOnly: Boolean = false,
    private val newCasesLog: () -> CasesLog
) :
    Composite<HorizontalLayout>(),
    HasSize,
    ThemableLayout {

    private val responsibilityTabs: Tabs = Tabs()
    private var responsibilityTabsSelectedRegistration: Registration? = null
    private val subscriberTabs: Tabs = Tabs()
    private val insuranceForm =
        InsuranceForm(sectionHeader("Insurance Payer"), readOnly).apply { width = "100%" }
    private val requisitionDiv = Div().apply {
        setSizeFull()
        isVisible = false
    }
    private val requisitionFormList =
        RequisitionFormList().apply {
            height = "100%"
            isAutoSelect = true
            onRequisitionsSelected = { requisitionForm ->
                if (requisitionForm == null) {
                    requisitionDiv.isVisible = false
                } else {
                    requisitionDiv.isVisible = true
                    requisitionDiv.removeAll()
                    requisitionDiv += ImgPanZoom().apply {
                        setSizeFull()
                        src = requisitionForm.jpg
                    }
                }
            }
        }
    private val subscriberForm = SubscriberForm(readOnly)
    private val subscriberInputTab = Tab("Information")
    private val eligibilityCheckResTab = Tab("Eligibility Raw Response Data")
    private val eligibilityCheckResView = EligibilityCheckResView().apply {
        isPadding = false
        width = "100%"
    }
    private val caseHeader = CaseHeader()

    var caseId: String? = null
        set(value) {
            insuranceForm.caseId = value
            requisitionFormList.caseId = value
            subscriberForm.caseId = value
            caseHeader.caseId = value
            field = value
        }

    var selectedResponsibilityOrder: ResponsibilityOrder? = null
    var selectedItem: IssueEligibility? = null
        set(new) {
            field = new
            selectedResponsibilityOrder = new?.responsibility?.let { ResponsibilityOrder.valueOf(it) }
        }

    var items: List<IssueEligibility>? = null
        set(new) {
            field = new
            updateItems(new)
        }

    private fun updateItems(newItems: List<IssueEligibility>?) {
        responsibilityTabsSelectedRegistration?.remove()

        val groupByResponsibility: Map<String, List<IssueEligibility>> =
            newItems?.groupBy { it.responsibility ?: "" } ?: emptyMap()

        val invalidResponsibility = mutableListOf<IssueEligibility?>()
        responsibilityTabs.removeAll()

        for (responsibility in groupByResponsibility) {
            try {
                responsibilityTabs += ResponsibilityTab(
                    ResponsibilityOrder.valueOf(responsibility.key),
                    responsibility.value.last()
                )
            } catch (e: IllegalArgumentException) {
                invalidResponsibility += responsibility.value.lastOrNull()
            }
        }
        if (invalidResponsibility.isNotEmpty()) {
            var nextResponsibility = responsibilityTabs
                .respTabs
                .map { it.order }
                .max()
                ?.ordinal
                ?.plus(1)
                ?: ResponsibilityOrder.Primary.ordinal

            for (order in ResponsibilityOrder.values()) {
                if (order.ordinal == nextResponsibility) {
                    val eligibility = invalidResponsibility.firstOrNull() ?: continue
                    invalidResponsibility.removeAt(0)
                    responsibilityTabs += ResponsibilityTab(order, eligibility)
                    nextResponsibility++
                }
            }
        }

        if (responsibilityTabs.respTabs.isEmpty()) {
            responsibilityTabs += ResponsibilityTab(ResponsibilityOrder.Primary)
        }

        val respTabs = responsibilityTabs.respTabs
        val firstTab = respTabs.first()
        val selectedTab =
            if (selectedResponsibilityOrder == null)
                firstTab else
                respTabs.firstOrNull { it.order == selectedResponsibilityOrder } ?: firstTab

        responsibilityTabsSelectedRegistration =
                responsibilityTabs.addSelectedChangeListener { event: Tabs.SelectedChangeEvent ->
                    selectResponsibilityTab(event.source.selectedTab as? ResponsibilityTab)
                }
        responsibilityTabs.selectedTab = selectedTab
        selectResponsibilityTab(selectedTab)
    }

    private fun selectResponsibilityTab(tab: ResponsibilityTab?) {
        selectedResponsibilityOrder = tab?.order
        val value = tab?.value
        insuranceForm.value = value?.insurance
        subscriberForm.value = value?.subscriber
        applyEligibilityCheckRes(value?.eligibility?.findEligibilityCheckRes())

        val respTabs = responsibilityTabs.respTabs
        addResponsibility.isEnabled = !readOnly &&
                respTabs.size < ResponsibilityOrder.values().size
        deleteResponsibility.isEnabled = !readOnly &&
                responsibilityTabs.selectedIndex > 0 &&
                respTabs.lastOrNull() == tab
        subscriberTabs.selectedIndex = 0
    }

    var onPatientEligibilityChecked: ((IssueEligibility, EligibilityCheckRes) -> Unit)? = null
    var onPatientEligibilitySave: ((IssueEligibility) -> Unit)? = null
    var onInsert: ((IssueEligibility) -> Unit)? = null
    var onRemove: (((IssueEligibility) -> Boolean) -> Unit)? = null
    var onCasesUpdated: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private infix fun VerticalLayout.expand(content: Component) {
        this += content
        setHorizontalComponentAlignment(FlexComponent.Alignment.END, content)
        setFlexGrow(1.0, content)
    }

    private fun actions(build: HorizontalLayout.() -> Unit = {}): HorizontalLayout =
        HorizontalLayout().apply {
            isPadding = false
            defaultVerticalComponentAlignment = FlexComponent.Alignment.END
            this += Button("Close").apply {
                element.setAttribute("theme", "contrast tertiary")
                addClickListener { onClose?.invoke() }
            }
            build()
        }


    private val addResponsibility = Button(VaadinIcon.PLUS.create()).apply {
        element.setAttribute("theme", "icon tertiary")
        addClickListener { _ ->
            val onInsert = onInsert
            val next = responsibilityTabs.respTabs.last().order.next
            if (onInsert != null && next != null) {
                val old = responsibilityTabs.selectedRespTab.value
                val new = old.copy(
                    origin = null,
                    responsibility = next.name,
                    insurance = null,
                    subscriber = old.subscriber?.copy(policyNumber = null),
                    eligibility = null
                )
                onInsert(new)
                responsibilityTabs.selectedTab = responsibilityTabs.respTabs.last()
            }
        }
    }

    private val deleteResponsibility = Button(VaadinIcon.MINUS.create()).apply {
        element.setAttribute("theme", "icon tertiary")
        addClickListener { _ ->
            val toDelete = responsibilityTabs.selectedRespTab.value.responsibility
            Dialog().also { dialog ->
                dialog += VerticalLayout().apply {
                    defaultHorizontalComponentAlignment = FlexComponent.Alignment.CENTER
                    this += H3("Do you wish to delete $toDelete Information?")
                    this += HorizontalLayout().apply {
                        this += Button("Delete").apply {
                            addClickListener { _ ->
                                onRemove?.invoke { it.responsibility == toDelete }
                                dialog.close()
                            }
                        }
                        this += Button("Cancel") { dialog.close() }
                    }
                }
                dialog.open()
            }
        }
    }

    private val checkResSum = EligibilityCheckResSum().apply {
        style["padding-top"] = "0.5em"
    }

    private val payersRecheck = PayersRecheck()

    private val saveWithNoVerificationButton = Button("Save with no verification").apply {
        isEnabled = !readOnly
        element.setAttribute("theme", "tertiary")
        addClickListener {
            if (insuranceForm.isValid && subscriberForm.isValid) {
                val insurance = insuranceForm.value
                onPatientEligibilitySave?.invoke(
                    responsibilityTabs.selectedRespTab.value
                        .copy(
                            origin = null,
                            responsibility = selectedResponsibilityOrder?.name,
                            insurance = insurance,
                            subscriber = subscriberForm.value,
                            eligibility = null,
                            status = when (insuranceForm.isPokitDokPayerNotAvailable) {
                                true -> IssueEligibility.Status.NotAvailable
                                false -> IssueEligibility.Status.Unchecked
                            }
                        )
                )
                insurance?.updatePayerLookups(
                    insuranceForm.isPokitDokPayerUpdated
                )
            }
        }
    }

    private val verifyEligibilityButton = Button("Verify Eligibility").apply {
        isEnabled = !readOnly
        element.setAttribute("theme", "primary")
        addClickListener {
            val pokitDokPayerUpdated = insuranceForm.isPokitDokPayerUpdated
            val insuranceValid = insuranceForm.checkFields()
            val subscriberValid = subscriberForm.checkFields()
            if (insuranceValid && subscriberValid) {
                val insurance = insuranceForm.value
                val issue =
                    responsibilityTabs.selectedRespTab.value
                        .copy(
                            origin = null,
                            responsibility = selectedResponsibilityOrder?.name,
                            insurance = insurance,
                            subscriber = subscriberForm.value,
                            eligibility = null
                        )
                val res = EligibilityChecker(issue, newCasesLog).check()
                issue.eligibility = (res as? EligibilityCheckRes.HasResult)?.id
                applyEligibilityCheckRes(res)
                onPatientEligibilityChecked?.invoke(issue, res)
                insurance?.updatePayerLookups(pokitDokPayerUpdated)
            }
        }
    }

    private fun applyEligibilityCheckRes(res: EligibilityCheckRes?) {
        eligibilityCheckResView.value = res
        checkResSum.value = res
        subscriberForm.banner = checkResSum.banner
        saveWithNoVerificationButton.isEnabled = !readOnly && res !is EligibilityCheckRes.Pass
        verifyEligibilityButton.isEnabled = !readOnly && res !is EligibilityCheckRes.Pass
    }

    init {
        val main = VerticalLayout().apply {
            isPadding = false
            this += HorizontalLayout().apply {
                isPadding = false
                isSpacing = false
                this += responsibilityTabs
                this += addResponsibility
                this += deleteResponsibility
            }
            this += VerticalLayout().apply {
                isPadding = false
                setSizeFull()

                val subscriberTabMap = mapOf<Tab, Component>(
                    subscriberInputTab to VerticalLayout().apply {
                        isPadding = false
                        this += Div().apply {
                            style["overflow"] = "auto"
                            style["width"] = "100%"
                            style["height"] = "100%"
                            this += HorizontalLayout().apply {
                                isPadding = false
                                width = "100%"
                                defaultVerticalComponentAlignment = FlexComponent.Alignment.START
                                this += insuranceForm
                                setFlexGrow(3.0, insuranceForm)
                            }
                            this += sectionHeader("Subscriber")
                            this += subscriberForm
                            this += checkResSum
                        }
                        this expand HorizontalLayout().apply {
                            isPadding = false
                            width = "100%"
                            defaultVerticalComponentAlignment = FlexComponent.Alignment.END
                            this += VerticalLayout().apply {
                                isPadding = false
                                width = "100%"
                                defaultHorizontalComponentAlignment = FlexComponent.Alignment.END
                                this += payersRecheck
                                this += actions {
                                    this += saveWithNoVerificationButton
                                    this += verifyEligibilityButton
                                }
                            }
                        }
                    },
                    eligibilityCheckResTab to VerticalLayout().apply {
                        isPadding = false
                        this += eligibilityCheckResView
                        this expand actions()
                    }
                )

                this += subscriberTabs.also { tabs ->
                    tabs.addSelectedChangeListener {
                        subscriberTabMap.showTab(tabs.selectedTab)
                    }
                }

                subscriberTabMap.addTabPages(subscriberTabs, this)
                subscriberTabMap.showTab(subscriberInputTab)
            }
        }

        val right = HorizontalLayout().apply {
            style["padding-left"] = "1em"
            isPadding = false
            setSizeFull()

            this += requisitionFormList
            this += VerticalLayout().apply {
                isPadding = false
                setSizeFull()
                defaultHorizontalComponentAlignment = FlexComponent.Alignment.END
                this += caseHeader
                this += requisitionDiv
            }
        }

        content.isPadding = false
        content.isSpacing = false

        content += main
        content += right

        content.setFlexGrow(1.0, main)
        content.setFlexGrow(1.0, right)
    }

    private fun Insurance.updatePayerLookups(pokitDokPayerUpdated: Boolean) {
        val caseId = caseId ?: return
        val payerName = payerName ?: return
        val payerLookup = PayerLookup()
        val oldPayerLookupId = payerLookup[payerName]?._id
        payerLookup[payerName] = zmPayerId
        if (pokitDokPayerUpdated || zmPayerId != oldPayerLookupId) {
            payersRecheck.show(caseId, payerName, zmPayerId, onCasesUpdated)
        }
    }

    class ResponsibilityTab(
        val order: ResponsibilityOrder,
        var value: IssueEligibility = IssueEligibility()
    ) : Tab(order.name + " Subscriber")

    class CaseHeader : Composite<HorizontalLayout>() {
        var caseId: String? = null
            set(value) {
                field = value
                updateUI()
            }

        private fun updateUI() {
            val case = caseId?.let { DBS.Collections.cases().find(it._id()).firstOrNull() }
            accession.text = case?.opt<String>("case", "Case", "accessionNumber") ?: ""
        }

        private val accession = Div().apply {
            style["background-color"] = "white"
            style["height"] = "1em"
            style["padding"] = "0.5em"
            style["z-index"] = "100"
        }

        init {
            content.isPadding = false
            content += accession
        }
    }

    companion object {
        private fun Map<Tab, Component>.showTab(tab: Tab) =
            forEach { it.value.isVisible = tab == it.key }

        private fun Map<Tab, Component>.addTabPages(tabs: Tabs, layout: VerticalLayout) =
            forEach { tab, page ->
                tabs += tab
                page.isVisible = false
                layout += page
                layout.setFlexGrow(1.0, page)
            }

        private fun sectionHeader(text: String) =
            H3(text).apply { style["margin-bottom"] = "0" }

        private val Tabs.respTabs: List<ResponsibilityTab>
            get() = components.filterIsInstance<ResponsibilityTab>().toList()

        private val Tabs.selectedRespTab: ResponsibilityTab
            get() = selectedTab as ResponsibilityTab

        private val ResponsibilityOrder?.next: ResponsibilityOrder?
            get() = ResponsibilityOrder.values().getOrNull(this?.ordinal?.plus(1) ?: 0)
    }

}

private fun String.findEligibilityCheckRes(): EligibilityCheckRes? =
    DBS.Collections
        .eligibility()
        .find(_id())
        .firstOrNull()
        ?.toEligibilityCheckRes()


private class EligibilityCheckResSum : VerticalLayout() {
    var value: EligibilityCheckRes? = null
        set(new) {
            field = new
            updateUI(new)
        }

    var banner: Component? = null

    init {
        isPadding = false
        setSizeUndefined()
    }

    private fun updateUI(value: EligibilityCheckRes?) {
        removeAll()
        banner = null
        value ?: return

        banner = when (value) {
            is EligibilityCheckRes.Pass -> PassBanner("Coverage active")
            is EligibilityCheckRes.Warn -> {
                val validRequest = value.result.opt<Boolean>("data", "valid_request") == true
                if (validRequest) {
                    val coverageActive = value.result.opt<Boolean>("data", "coverage", "active")
                    when (coverageActive) {
                        false -> ErrorBanner("Coverage not active")
                        else -> WarnBanner("Coverage unknown")
                    }
                } else
                    ErrorBanner("Request not valid")
            }
            EligibilityCheckRes.NotAvailable -> PassBanner("Payer Not Available")
            is EligibilityCheckRes.Error -> ErrorBanner(value.message)
        }

        when (value) {
            is EligibilityCheckRes.Pass -> listOfNotNull(
                value.result.opt<String>("data", "coverage", "insurance_type")?.toTitleCase()
                    ?.let { "Insurance Type: $it" },
                value.result.opt<String>("data", "coverage", "plan_description")?.toTitleCase()
                    ?.let { "Plan Description: $it" }
            )
            is EligibilityCheckRes.Warn -> listOfNotNull(
                value.result.opt<String>("data", "reject_reason")?.toTitleCase(),
                value.result.opt<String>("data", "follow_up_action")?.toTitleCase()
            )
            else -> emptyList()
        }.forEach {
            this += Div(Text(it))
        }
    }

    companion object {
        private abstract class Banner(
            label: String,
            icon: VaadinIcon,
            backgroundColor: String,
            color: String = "white"
        ) : Div() {
            init {
                style["padding"] = "0.3em"
                style["color"] = color
                style["background-color"] = backgroundColor
                this += icon.create().apply { style["padding-right"] = "0.5em" }
                this += Span(label)
            }
        }

        private class PassBanner(label: String) : Banner(label, VaadinIcon.CHECK, "#1e8e3e")
        private class WarnBanner(label: String) : Banner(label, VaadinIcon.WARNING, "#f4b400")
        private class ErrorBanner(label: String) : Banner(label, VaadinIcon.EXCLAMATION_CIRCLE, "#d23f31")
    }
}

private class PayersRecheck : HorizontalLayout() {
    private var toRecheck: List<Item> = emptyList()
    private var onRecheckFinished: (() -> Unit)? = null
    private val note = Span()
    private val button = Button("").apply {
        addClickListener { toRecheck.recheck() }
    }

    init {
        this += note
        this += button
        isVisible = false
    }

    private class Item(
        val caseHist: CaseHist,
        val zmPayerId: String,
        val eligibilityList: MutableList<IssueEligibility> = mutableListOf()
    )

    private fun List<Item>.recheck() {
        isVisible = false
        val payerLookup = PayerLookup()
        forEach { item ->
            val eligibilityCheckContext =
                EligibilityCheckContext(
                    payerLookup = payerLookup,
                    newCasesLog = { item.caseHist.toCasesLog() })
            val checked =
                item.eligibilityList
                    .asSequence()
                    .map { it.deepCopy() }
                    .map {
                        it.insurance?.zmPayerId = item.zmPayerId
                        // TODO is zmPayerName required?
                        it.checkEligibility(eligibilityCheckContext)
                    }
                    .toList()
            item.caseHist.eligibility += checked
            item.caseHist.update(
                UpdateContext(
                    source = ".user",
                    action = "hist.eligibility.add rechecked",
                    message = "Recheck related issues",
                    user = ui.orElse(null)?.casesUser?.email
                )
            )
        }
        onRecheckFinished?.invoke()
    }

    fun show(caseId: String, payerName: String, zmPayerId: String?, onCasesUpdated: (() -> Unit)?) {
        zmPayerId ?: return
        onRecheckFinished = onCasesUpdated
        toRecheck = buildToRecheck(caseId, payerName, zmPayerId)
        isVisible = toRecheck.isNotEmpty()
        note.text = "Payer has been changed."
        button.text = "Recheck ${toRecheck.size} related issues"
    }

    private fun buildToRecheck(caseId: String, payerName: String, zmPayerId: String): List<Item> {
        val items = mutableMapOf<String, Item>()
        DBS.Collections.cases()
            .find(doc { self["hist.eligibility.insurance.payerName"] = payerName })
            .map { it.toCaseHist() }
            .filterNot { caseId == it._id }
            .filter { it.status?.isEditable == true }
            .forEach { case: CaseHist ->
                case.eligibility
                    .groupBy { it.responsibility }
                    .values
                    .forEach { eligibilityList: List<IssueEligibility> ->
                        eligibilityList.lastOrNull()?.let { it: IssueEligibility ->
                            items
                                .getOrPut(case._id) { Item(case, zmPayerId) }
                                .eligibilityList += it
                        }
                    }
            }
        return items.values.toList()
    }
}
