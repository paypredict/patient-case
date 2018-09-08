package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.Route
import net.paypredict.patient.cases.data.worklist.IssueEligibility
import kotlin.properties.Delegates

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/25/2018.
 */
@Route("eligibility")
class PatientEligibilityForm : Composite<VerticalLayout>(), HasSize, ThemableLayout {
    private var insuranceForm = InsuranceForm().apply { width = "100%" }
    private var subscriberForm = SubscriberForm()

    var value: IssueEligibility?
            by Delegates.observable(null) { _, _: IssueEligibility?, new: IssueEligibility? ->
                insuranceForm.value = new?.insurance
                subscriberForm.value = new?.subscriber
            }

    var checkPatientEligibility: ((IssueEligibility) -> Unit)? = null
    var savePatientEligibility: ((IssueEligibility) -> Unit)? = null

    init {
        content += H2("Subscriber Information")
        content += sectionHeader("Insurance Payer")
        content += insuranceForm
        content += sectionHeader("Subscriber")
        content += subscriberForm

        content += HorizontalLayout().apply {
            isPadding = false
            this += VerticalLayout().apply {
                isPadding = false
                this += Button("Verify Eligibility").apply {
                    element.setAttribute("theme", "primary")
                    isEnabled = false
                }
                this += Button("Save with no verification").apply {
                    element.setAttribute("theme", "tertiary")
                    addClickListener {
                        savePatientEligibility?.invoke(
                            (value ?: IssueEligibility())
                                .copy(insurance = insuranceForm.value)
                                .copy(subscriber = subscriberForm.value)
                        )
                    }
                }
            }
        }
    }

    private fun sectionHeader(text: String) =
        H3(text).apply { style["margin-bottom"] = "0" }

}