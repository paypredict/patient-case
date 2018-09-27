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
import net.paypredict.patient.cases.data.worklist.IssueAddress
import net.paypredict.patient.cases.html.ImgPanZoom
import kotlin.properties.Delegates

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/28/2018.
 */
@Route("address")
class AddressForm : Composite<HorizontalLayout>(), HasSize, ThemableLayout {
    private var binder: Binder<IssueAddress> = Binder()
    var value: IssueAddress?
            by Delegates.observable(null) { _, _: IssueAddress?, new: IssueAddress? ->
                binder.readBean(new)
            }

    var caseId: String? = null
        set(value) {
            requisitionFormList.caseId = value
            field = value
        }

    private val requisitionLayout = VerticalLayout().apply {
        isPadding = false
        setSizeFull()
    }

    private val requisitionFormList =
        RequisitionFormList().apply {
            isAutoSelect = true
            onRequisitionsSelected = { requisitionForm ->
                if (requisitionForm != null) {
                    requisitionLayout.removeAll()
                    requisitionLayout += ImgPanZoom().apply {
                        src = requisitionForm.jpg
                        setSizeFull()
                    }
                }
            }
        }

    var checkPatientAddress: ((IssueAddress) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    init {
        val form = FormLayout().apply {
            width = "100%"
            height = null
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

            this += VerticalLayout().apply {
                isPadding = false
                element.setAttribute("colspan", "5")
                defaultHorizontalComponentAlignment = FlexComponent.Alignment.END
                this += HorizontalLayout().apply {
                    isPadding = false
                    this += Button("Close").apply {
                        element.setAttribute("theme", "contrast tertiary")
                        addClickListener { onClose?.invoke() }
                    }
                    this += Button("Check Address").apply {
                        element.setAttribute("theme", "primary")
                        isEnabled = false
                        addClickListener { value?.run { checkPatientAddress?.invoke(this) } }
                    }
                }
            }

            setResponsiveSteps(
                FormLayout.ResponsiveStep("10em", 1),
                FormLayout.ResponsiveStep("20em", 2),
                FormLayout.ResponsiveStep("30em", 3),
                FormLayout.ResponsiveStep("40em", 4),
                FormLayout.ResponsiveStep("50em", 5)
            )
        }

        val main = HorizontalLayout().apply {
            isPadding = false
            width = "100%"
            this += VerticalLayout().apply {
                isPadding = false
                width = "100%"
                this += H2("Patient Address Check")
                this += form
            }
            this += requisitionFormList
        }

        content.isPadding = false
        content.isSpacing = false

        content += main
        content += requisitionLayout
        content.setFlexGrow(1.0, main)
        content.setFlexGrow(1.0, requisitionLayout)
    }

}