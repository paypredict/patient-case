package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.binder.Binder
import com.vaadin.flow.data.binder.ValidationResult
import com.vaadin.flow.data.binder.ValueContext
import com.vaadin.flow.router.Route
import net.paypredict.patient.cases.apis.smartystreets.FootNote
import net.paypredict.patient.cases.apis.smartystreets.FootNoteSet
import net.paypredict.patient.cases.data.worklist.IssueAddress
import net.paypredict.patient.cases.html.ImgPanZoom

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/28/2018.
 */
@Route("address")
class AddressForm : Composite<HorizontalLayout>(), HasSize, ThemableLayout {
    private var binder: Binder<IssueAddress> = Binder()
    var value: IssueAddress? = null
        get() {
            if (field == null) field = IssueAddress()
            binder.writeBeanIfValid(field)
            return field
        }
        set(new) {
            binder.readBean(new)
            updateFootnotes(new?.footNoteSet ?: emptySet())
            field = new
        }

    val isValid: Boolean
        get() = binder.validate().isOk

    var caseId: String? = null
        set(value) {
            requisitionFormList.caseId = value
            field = value
        }

    private val footnotes = VerticalLayout().apply {
        isPadding = false
        isSpacing = false
    }

    private fun updateFootnotes(footNoteSet: FootNoteSet) {
        footnotes.removeAll()
        for (footNote in footNoteSet) {
            footnotes += HorizontalLayout().apply {
                isPadding = false
                defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
                this += when (footNote.level) {
                    FootNote.Level.INFO -> VaadinIcon.CHECK.create().apply { color = "#1e8e3e" }
                    FootNote.Level.WARNING -> VaadinIcon.WARNING.create().apply { color = "#f4b400" }
                    FootNote.Level.ERROR -> VaadinIcon.EXCLAMATION_CIRCLE.create().apply { color = "#d23f31" }
                }.apply {
                    style["width"] = "1em"
                    style["height"] = "1em"
                }
                this += Span(footNote.label).apply {
                    style["font-weight"] = "500"
                }
            }
            footnotes += HorizontalLayout().apply {
                isPadding = false
                this += Span(footNote.note).apply { style["padding-left"] = "2em" }
            }
        }
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
                element.setAttribute("colspan", "3")
                binder
                    .forField(this)
                    .withValidator(fieldIsRequired)
                    .bind(
                        IssueAddress::address1.getter,
                        IssueAddress::address1.setter
                    )
            }
            this += TextField("Address line 2").apply {
                element.setAttribute("colspan", "3")
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
                element.setAttribute("colspan", "3")
                defaultHorizontalComponentAlignment = FlexComponent.Alignment.END
                this += HorizontalLayout().apply {
                    isPadding = false
                    this += Button("Close").apply {
                        element.setAttribute("theme", "contrast tertiary")
                        addClickListener { onClose?.invoke() }
                    }
                    this += Button("Check Address").apply {
                        element.setAttribute("theme", "primary")
                        addClickListener {
                            val validationStatus = binder.validate()
                            if (validationStatus.isOk)
                                value?.run { checkPatientAddress?.invoke(this) }
                            else
                                Dialog().apply { this += H3("Check fields errors") }.open()
                        }
                    }
                }
            }

            setResponsiveSteps(
                FormLayout.ResponsiveStep("10em", 1),
                FormLayout.ResponsiveStep("20em", 2),
                FormLayout.ResponsiveStep("30em", 3)
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
                this += footnotes
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