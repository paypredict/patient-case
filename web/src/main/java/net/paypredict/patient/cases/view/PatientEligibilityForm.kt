package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
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
import net.paypredict.patient.cases.data.worklist.IssueEligibility
import net.paypredict.patient.cases.html.ImgPanZoom
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityCheckRes
import net.paypredict.patient.cases.pokitdok.eligibility.EligibilityChecker
import kotlin.properties.Delegates

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/25/2018.
 */
@Route("eligibility")
class PatientEligibilityForm : Composite<HorizontalLayout>(), HasSize, ThemableLayout {
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

    var caseId: String? = null
        set(value) {
            insuranceForm.caseId = value
            requisitionFormList.caseId = value
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
                addClickListener { onCancel?.invoke() }
            }
            build()
        }

    init {
        val main = VerticalLayout().apply {
            isPadding = false
            this += HorizontalLayout().apply {
                isPadding = false
                isSpacing = false
                this += Tabs().apply {
                    this += Tab(Order.Primary.name + " Subscriber")
                }
                this += Button(VaadinIcon.PLUS.create()).apply {
                    element.setAttribute("theme", "icon tertiary")
                }
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

        val requisitions = HorizontalLayout().apply {
            isPadding = false
            style["padding-left"] = "1em"
            setSizeFull()

            this += requisitionFormList
            this += requisitionDiv
        }

        content.isPadding = false
        content.isSpacing = false

        content += main
        content += requisitions

        content.setFlexGrow(1.0, main)
        content.setFlexGrow(1.0, requisitions)
    }

    enum class Order {
        Primary, Secondary, Tertiary,
        Quaternary, Quinary, Senary,
        Septenary, Octonary, Nonary, Denary
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
    }

}