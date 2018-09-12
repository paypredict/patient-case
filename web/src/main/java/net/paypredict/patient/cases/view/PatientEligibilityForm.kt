package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.tabs.Tab
import com.vaadin.flow.component.tabs.Tabs
import com.vaadin.flow.router.Route
import net.paypredict.patient.cases.data.worklist.IssueEligibility
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityCheckRes
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityChecker
import kotlin.properties.Delegates

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/25/2018.
 */
@Route("eligibility")
class PatientEligibilityForm : Composite<VerticalLayout>(), HasSize, ThemableLayout {
    private lateinit var tabs: Tabs
    private val insuranceForm =
        InsuranceForm(sectionHeader("Insurance Payer")).apply { width = "100%" }
    private val requisitionsView =
        RequisitionsView(sectionHeader("Requisition Forms")).apply { setSizeUndefined() }
    private val subscriberForm = SubscriberForm()
    private val eligibilityCheckResTab = Tab("Eligibility")
    private val eligibilityCheckResView = EligibilityCheckResView().apply {
        isPadding = false
        width = "100%"
    }

    var caseId: String? = null
        set(value) {
            insuranceForm.caseId = value
            requisitionsView.caseId = value
            subscriberForm.caseId = value
            field = value
        }

    var value: IssueEligibility?
            by Delegates.observable(null) { _, _: IssueEligibility?, new: IssueEligibility? ->
                insuranceForm.value = new?.insurance
                subscriberForm.value = new?.subscriber
                eligibilityCheckResView.value = new?.eligibility
            }

    var onPatientEligibilityChecked: ((IssueEligibility, EligibilityCheckRes) -> Unit)? = null
    var onPatientEligibilitySave: ((IssueEligibility) -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private val actions = HorizontalLayout().apply {
        isPadding = false
        defaultVerticalComponentAlignment = FlexComponent.Alignment.END
        this += Button("Close").apply {
            element.setAttribute("theme", "contrast tertiary")
            addClickListener { onCancel?.invoke() }
        }
        this += Button("Save with no verification").apply {
            element.setAttribute("theme", "tertiary")
            addClickListener {
                if (insuranceForm.isValid && subscriberForm.isValid)
                    onPatientEligibilitySave?.invoke(
                        (value ?: IssueEligibility())
                            .copy(insurance = insuranceForm.value)
                            .copy(subscriber = subscriberForm.value)
                    )
            }
        }
        this += Button("Verify Eligibility").apply {
            element.setAttribute("theme", "primary")
            addClickListener {
                if (insuranceForm.isValid && subscriberForm.isValid) {
                    val issue = IssueEligibility(
                        insurance = insuranceForm.value,
                        subscriber = subscriberForm.value
                    )
                    val res = EligibilityChecker(issue).check()
                    val eligibilityRes = if (res is EligibilityCheckRes.HasResult) res.id else null
                    issue.eligibility = eligibilityRes
                    eligibilityCheckResView.value = eligibilityRes
                    if (eligibilityRes != null) tabs.selectedTab = eligibilityCheckResTab
                    onPatientEligibilityChecked?.invoke(issue, res)
                }
            }
        }
    }


    init {
        content += sectionHeader("Subscriber Information")

        val tabMap = mutableMapOf<Tab, Component>()
        val inputTab = Tab("Subscriber")
        tabMap[inputTab] = VerticalLayout().apply {
            isPadding = false
            this += HorizontalLayout().apply {
                isPadding = false
                width = "100%"
                defaultVerticalComponentAlignment = FlexComponent.Alignment.START
                this += insuranceForm
                this += requisitionsView
                setFlexGrow(3.0, insuranceForm)
            }
            this += sectionHeader("Subscriber")
            this += subscriberForm
            this += actions
            setHorizontalComponentAlignment(FlexComponent.Alignment.END, actions)
            setFlexGrow(1.0, actions)
        }

        tabMap[eligibilityCheckResTab] = eligibilityCheckResView

        fun Tab.select() {
            tabMap.forEach {
                it.value.isVisible = this == it.key
            }
        }

        tabs = Tabs().apply {
            tabMap.keys.forEach { add(it) }
            addSelectedChangeListener {
                selectedTab.select()
            }
        }
        content += tabs
        tabMap.values.forEach {
            content += it
            content.setFlexGrow(1.0, it)
            it.isVisible = false
        }
        inputTab.select()
    }

    private fun sectionHeader(text: String) =
        H3(text).apply { style["margin-bottom"] = "0" }

}