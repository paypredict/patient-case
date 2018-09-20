package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
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
import net.paypredict.patient.cases.data.worklist.*
import kotlin.properties.Delegates

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/28/2018.
 */
@Route("address")
class AddressForm : Composite<VerticalLayout>(), HasSize, ThemableLayout {
    private var binder: Binder<IssueAddress> = Binder()
    var value: IssueAddress?
            by Delegates.observable(null) { _, _: IssueAddress?, new: IssueAddress? ->
                binder.readBean(new)
            }

    var checkPatientAddress: ((IssueAddress) -> Unit)? = null

    init {
        val form = FormLayout().apply {
            width = "100%"
            val fieldIsRequired: (Any?, ValueContext) -> ValidationResult = { value: Any?, _ ->
                if (value == null || value is String && value.isBlank())
                    ValidationResult.error("Field Is Required") else
                    ValidationResult.ok()
            }
            this += TextField("Address line 1").apply {
                isRequired = true
                binder
                    .forField(this)
                    .withValidator(fieldIsRequired)
                    .bind(
                        IssueAddress::address1.getter,
                        IssueAddress::address1.setter
                    )
            }
            this += TextField("Address line 2").apply {
                binder
                    .forField(this)
                    .bind(
                        IssueAddress::address2.getter,
                        IssueAddress::address2.setter
                    )
            }
            this += TextField("City").apply {
                isRequired = true
                binder
                    .forField(this)
                    .withValidator(fieldIsRequired)
                    .bind(
                        IssueAddress::city.getter,
                        IssueAddress::city.setter
                    )
            }
            this += TextField("ZIP").apply {
                isRequired = true
                binder
                    .forField(this)
                    .withValidator(fieldIsRequired)
                    .bind(
                        IssueAddress::zip.getter,
                        IssueAddress::zip.setter
                    )
            }
            this += TextField("State").apply {
                isRequired = true
                binder
                    .forField(this)
                    .withValidator(fieldIsRequired)
                    .bind(
                        IssueAddress::state.getter,
                        IssueAddress::state.setter
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
            val checkAddress = Button("Check Address").apply {
                isVisible = false
                element.setAttribute("theme", "primary")
            }
            var valueOnNewRequest: IssueAddress? = IssueAddress()

            newRequest.addClickListener {
                valueOnNewRequest = value
                binder.setReadOnly(false)
                cancel.isVisible = true
                checkAddress.isVisible = true
                newRequest.isVisible = false
            }
            cancel.addClickListener {
                value = valueOnNewRequest
                binder.setReadOnly(true)
                cancel.isVisible = false
                checkAddress.isVisible = false
                newRequest.isVisible = true
            }
            checkAddress.addClickListener {
                val new = valueOnNewRequest ?: IssueAddress()
                if (binder.writeBeanIfValid(new)) {
                    checkPatientAddress?.invoke(new)
                    binder.setReadOnly(true)
                    cancel.isVisible = false
                    checkAddress.isVisible = false
                    newRequest.isVisible = true
                }
                binder.bean = null
            }
            this += newRequest
            this += cancel
            this += checkAddress
        }

        form += VerticalLayout().apply {
            isPadding = false
            element.setAttribute("colspan", "3")
//            this += actions
            defaultHorizontalComponentAlignment = FlexComponent.Alignment.END
        }

        content += H2("Patient Address Check")
        content += form

    }

}