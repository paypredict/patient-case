package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.tabs.Tab
import com.vaadin.flow.component.tabs.Tabs
import com.vaadin.flow.router.Route
import com.vaadin.flow.shared.Registration
import net.paypredict.patient.cases.data.worklist.IssueEligibility
import net.paypredict.patient.cases.data.worklist.ResponsibilityOrder
import net.paypredict.patient.cases.html.ImgPanZoom
import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo._id
import net.paypredict.patient.cases.mongo.opt
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityCheckRes
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityChecker

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/25/2018.
 */
@Route("eligibility")
class PatientEligibilityForm : Composite<HorizontalLayout>(), HasSize, ThemableLayout {
    private val responsibilityTabs: Tabs = Tabs()
    private var responsibilityTabsSelectedRegistration: Registration? = null
    private val subscriberTabs: Tabs = Tabs()
    private val insuranceForm =
        InsuranceForm(sectionHeader("Insurance Payer")).apply { width = "100%" }
    private val requisitionDiv = Div().apply {
        setSizeFull()
        isVisible = false
    }
    private val requisitionFormList =
        RequisitionFormList().apply {
            height = "100%"
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
    private val subscriberForm = SubscriberForm()
    private val subscriberInputTab = Tab("Information")
    private val eligibilityCheckResTab = Tab("Eligibility")
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
        eligibilityCheckResView.value = value?.eligibility

        addResponsibility.isEnabled = items?.none { it.origin == "casesRaw" } ?: true
        deleteResponsibility.isEnabled = value?.origin == null && items?.size ?: 0 > 1
        subscriberTabs.selectedIndex = 0
    }

    var onPatientEligibilityChecked: ((IssueEligibility, EligibilityCheckRes) -> Unit)? = null
    var onPatientEligibilitySave: ((IssueEligibility) -> Unit)? = null
    var onInsert: ((IssueEligibility) -> Unit)? = null
    var onRemove: (((IssueEligibility) -> Boolean) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private infix fun VerticalLayout.withActions(actions: HorizontalLayout) {
        this += actions
        setHorizontalComponentAlignment(FlexComponent.Alignment.END, actions)
        setFlexGrow(1.0, actions)
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
            val toDelete = responsibilityTabs.selectedRespTab.value
            Dialog().also { dialog ->
                dialog += VerticalLayout().apply {
                    defaultHorizontalComponentAlignment = FlexComponent.Alignment.CENTER
                    this += H3("Do you wish to delete ${responsibilityTabs.selectedTab.label} Information?")
                    this += HorizontalLayout().apply {
                        this += Button("Delete") { _ ->
                            onRemove?.invoke { it == toDelete }
                            dialog.close()
                        }
                        this += Button("Cancel") { dialog.close() }
                    }
                }
                dialog.open()
            }
        }
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
                        }
                        this withActions actions {
                            this += Button("Save with no verification").apply {
                                element.setAttribute("theme", "tertiary")
                                addClickListener {
                                    if (insuranceForm.isValid && subscriberForm.isValid)
                                        onPatientEligibilitySave?.invoke(
                                            responsibilityTabs.selectedRespTab.value
                                                .copy(
                                                    origin = null,
                                                    responsibility = selectedResponsibilityOrder?.name,
                                                    insurance = insuranceForm.value,
                                                    subscriber = subscriberForm.value,
                                                    eligibility = null
                                                )
                                        )
                                }
                            }
                            this += Button("Verify Eligibility").apply {
                                element.setAttribute("theme", "primary")
                                addClickListener {
                                    if (insuranceForm.isValid && subscriberForm.isValid) {
                                        val issue =
                                            responsibilityTabs.selectedRespTab.value
                                                .copy(
                                                    origin = null,
                                                    responsibility = selectedResponsibilityOrder?.name,
                                                    insurance = insuranceForm.value,
                                                    subscriber = subscriberForm.value,
                                                    eligibility = null
                                                )
                                        val res = EligibilityChecker(issue).check()
                                        val eligibilityRes = if (res is EligibilityCheckRes.HasResult) res.id else null
                                        issue.eligibility = eligibilityRes
                                        eligibilityCheckResView.value = eligibilityRes
                                        if (eligibilityRes != null) subscriberTabs.selectedTab = eligibilityCheckResTab
                                        onPatientEligibilityChecked?.invoke(issue, res)
                                    }
                                }
                            }
                        }
                    },
                    eligibilityCheckResTab to VerticalLayout().apply {
                        isPadding = false
                        this += eligibilityCheckResView
                        this withActions actions()
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
            val case = caseId?.let { DBS.Collections.casesRaw().find(it._id()).firstOrNull() }
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