package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.binder.Binder
import com.vaadin.flow.data.binder.ValidationResult
import com.vaadin.flow.data.binder.ValueContext
import com.vaadin.flow.router.Route
import net.paypredict.patient.cases.data.worklist.Insurance
import net.paypredict.patient.cases.data.worklist.IssueEligibility
import net.paypredict.patient.cases.data.worklist.Subscriber
import net.paypredict.patient.cases.data.worklist.formatAs
import java.time.LocalDate
import kotlin.properties.Delegates

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/25/2018.
 */
@Route("eligibility")
class PatientEligibilityForm : Composite<VerticalLayout>(), HasSize, ThemableLayout {
    private var binder: Binder<IssueEligibility> = Binder()
    var value: IssueEligibility?
            by Delegates.observable(null) { _, _: IssueEligibility?, new: IssueEligibility? ->
                binder.readBean(new)
            }

    var checkPatientEligibility: ((IssueEligibility) -> Unit)? = null

    init {
        val form = FormLayout().apply {
            width = "100%"
            val fieldIsRequired: (Any?, ValueContext) -> ValidationResult = { value: Any?, _ ->
                if (value == null || value is String && value.isBlank())
                    ValidationResult.error("Field Is Required") else
                    ValidationResult.ok()
            }
            this += TextField("Subscriber Last Name").apply {
                isRequired = true
                binder
                    .forField(this)
                    .withValidator(fieldIsRequired)
                    .bind(
                        { it.subscriber?.lastName },
                        { item, value ->
                            item.subscriber = (item.subscriber ?: Subscriber()).copy(lastName = value)
                        }
                    )
            }
            this += TextField("Subscriber First Name").apply {
                isRequired = true
                binder
                    .forField(this)
                    .withValidator(fieldIsRequired)
                    .bind(
                        { it.subscriber?.firstName },
                        { item, value ->
                            item.subscriber = (item.subscriber ?: Subscriber()).copy(firstName = value)
                        }
                    )
            }
            this += DatePicker("Subscriber Date Of Birth").apply {
                isRequired = true
                binder
                    .forField(this)
                    .withValidator(fieldIsRequired)
                    .bind(
                        { it?.subscriber?.dobAsLocalDate },
                        { item: IssueEligibility?, value: LocalDate? ->
                            item?.subscriber = (item?.subscriber ?: Subscriber())
                                .copy(dob = value?.let { it formatAs Subscriber.dateFormat })
                        }
                    )
            }

            this += TextField("Insurance Carrier").apply {
                isRequired = true
                binder
                    .forField(this)
                    .withValidator(fieldIsRequired)
                    .bind(
                        { it.insurance?.payerName },
                        { item, value ->
                            item.insurance = (item.insurance ?: Insurance()).copy(payerName = value)
                        }
                    )
            }

            this += TextField("Subscriber Member ID").apply {
                isRequired = true
                binder
                    .forField(this)
                    .withValidator(fieldIsRequired)
                    .bind(
                        { it.subscriber?.policyNumber },
                        { item, value ->
                            item.subscriber = (item.subscriber ?: Subscriber()).copy(policyNumber = value)
                        }
                    )
            }

            setResponsiveSteps(
                FormLayout.ResponsiveStep("0", 1),
                FormLayout.ResponsiveStep("32em", 2),
                FormLayout.ResponsiveStep("32em", 3)
            )
        }
        binder.setReadOnly(true)

        val actions = HorizontalLayout().apply {
            val newRequest = Button("New Request")
            val cancel = Button("Cancel").apply { isVisible = false }
            val checkEligibility = Button("Check Eligibility").apply {
                isVisible = false
                element.setAttribute("theme", "primary")
            }
            var valueOnNewRequest: IssueEligibility? = IssueEligibility()

            newRequest.addClickListener {
                valueOnNewRequest = value
                binder.setReadOnly(false)
                cancel.isVisible = true
                checkEligibility.isVisible = true
                newRequest.isVisible = false
            }
            cancel.addClickListener {
                value = valueOnNewRequest
                binder.setReadOnly(true)
                cancel.isVisible = false
                checkEligibility.isVisible = false
                newRequest.isVisible = true
            }
            checkEligibility.addClickListener {
                val new = valueOnNewRequest ?: IssueEligibility()
                if (binder.writeBeanIfValid(new)) {
                    checkPatientEligibility?.invoke(new)
                    binder.setReadOnly(true)
                    cancel.isVisible = false
                    checkEligibility.isVisible = false
                    newRequest.isVisible = true
                }
                binder.bean = null
            }
            this += newRequest
            this += cancel
            this += checkEligibility
        }

        form += VerticalLayout().apply {
            isPadding = false
            element.setAttribute("colspan", "3")
            this += actions
            defaultHorizontalComponentAlignment = FlexComponent.Alignment.END
        }

        content += H2("Patient Eligibility Request")
        content += form

    }

}