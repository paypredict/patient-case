package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.binder.Binder
import com.vaadin.flow.data.binder.ValidationResult
import com.vaadin.flow.data.binder.ValueContext
import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo.opt
import net.paypredict.patient.cases.data.worklist.Person
import net.paypredict.patient.cases.data.worklist.Subscriber
import net.paypredict.patient.cases.data.worklist.formatAs
import net.paypredict.patient.cases.mongo._id
import org.bson.Document
import java.time.LocalDate

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/4/2018.
 */
class SubscriberForm : Composite<FormLayout>(), HasSize, ThemableLayout {
    private var binder: Binder<Subscriber> = Binder()
    private val fieldIsRequired: (Any?, ValueContext) -> ValidationResult = { value: Any?, _ ->
        if (value == null || value is String && value.isBlank())
            ValidationResult.error("Field Is Required") else
            ValidationResult.ok()
    }
    private val relationshipCode: ComboBox<String?> = ComboBox<String?>("Relation with patient").apply {
        isAllowCustomValue = false
        isPreventInvalidInput = true
        setItems(relationshipCodeItems)
        binder
            .forField(this)
            .bind(
                Subscriber::relationshipCode.getter,
                Subscriber::relationshipCode.setter
            )
    }
    private val firstName = TextField("First Name").apply {
        isRequired = true
        binder
            .forField(this)
            .withValidator(fieldIsRequired)
            .bind(
                Subscriber::firstName.getter,
                Subscriber::firstName.setter
            )
    }
    private val lastName = TextField("Last Name").apply {
        isRequired = true
        binder
            .forField(this)
            .withValidator(fieldIsRequired)
            .bind(
                Subscriber::lastName.getter,
                Subscriber::lastName.setter
            )
    }
    private val mi = TextField("MI").apply {
        binder
            .forField(this)
            .bind(
                Subscriber::mi.getter,
                Subscriber::mi.setter
            )
    }
    private val gender: ComboBox<String?> = ComboBox<String?>("Gender").apply {
        isRequired = true
        isAllowCustomValue = false
        isPreventInvalidInput = true
        setItems(genderItems)
        binder
            .forField(this)
            .bind(
                Subscriber::gender.getter,
                Subscriber::gender.setter
            )
    }
    private val dob = DatePicker("Date Of Birth").apply {
        isRequired = true
        binder
            .forField(this)
            .withValidator(fieldIsRequired)
            .bind(
                { it.dobAsLocalDate },
                { item: Subscriber?, value: LocalDate? ->
                    item?.dob = value?.let { it formatAs Subscriber.dateFormat }
                }
            )
    }
    private val policyNumber = TextField("Member ID").apply {
        isRequired = true
        binder
            .forField(this)
            .withValidator(fieldIsRequired)
            .bind(
                Subscriber::policyNumber.getter,
                Subscriber::policyNumber.setter
            )
    }

    private val casePatient = Span()

    var caseId: String? = null
        set(new) {
            field = new
            casePatient.text = new?.findCasePatient()?.toString() ?: ""
        }

    var value: Subscriber? = null
        get() {
            if (field == null) field = Subscriber()
            binder.writeBeanIfValid(field)
            return field
        }
        set(new) {
            binder.readBean(new)
            field = new
        }

    val isValid: Boolean
        get() = binder.validate().isOk

    private val bannerContainer = Div().apply {
        element.setAttribute("colspan", "2")
    }

    var banner: Component? = null
        set(new) {
            field = new
            bannerContainer.removeAll()
            if (new != null) bannerContainer += new
        }

    init {
        content += HorizontalLayout().apply {
            width = "100%"
            element.setAttribute("colspan", "5")
            isPadding = false
            defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
            this += Button("Copy from Patient").apply {
                element.setAttribute("theme", "tertiary-inline")
                addClickListener {
                    showCopyFromPatientDialog()
                }
            }
            this += casePatient
        }

        content += firstName
        content += lastName
        content += mi
        content += gender
        content += dob
        content += policyNumber
        content += relationshipCode
        content += bannerContainer

        content.setResponsiveSteps(
            FormLayout.ResponsiveStep("10em", 1),
            FormLayout.ResponsiveStep("20em", 2),
            FormLayout.ResponsiveStep("30em", 3),
            FormLayout.ResponsiveStep("40em", 4),
            FormLayout.ResponsiveStep("50em", 5)
        )
    }

    private fun String.findCasePatient(): Person? =
        DBS.Collections.casesRaw()
            .find(_id())
            .mapNotNull { case ->
                case.opt<Document>("case", "Case", "Patient")?.let { patient ->
                    Person(
                        firstName = patient.opt("firstName"),
                        mi = patient.opt("middleInitials"),
                        lastName = patient.opt("lastName"),
                        gender = patient.opt("gender"),
                        dob = patient.opt("dateOfBirth")
                    )
                }
            }
            .firstOrNull()

    private fun showCopyFromPatientDialog() {
        val patient = caseId?.findCasePatient()
        if (patient == null) {
            Notification.show("Patient data not found")
            return
        }
        Dialog().also { dialog ->
            dialog += VerticalLayout().apply {
                defaultHorizontalComponentAlignment = FlexComponent.Alignment.CENTER
                this += H3("Copy from Patient will override Subscriber data")
                this += HorizontalLayout().apply {
                    this += Button("Cancel") { dialog.close() }
                    this += Button("Override").apply {
                        element.setAttribute("theme", "error primary")
                        addClickListener { _ ->
                            relationshipCode.value = "SEL"
                            firstName.value = patient.firstName ?: ""
                            lastName.value = patient.lastName ?: ""
                            mi.value = patient.mi ?: ""
                            gender.value = patient.gender ?: ""
                            dob.value = patient.dobAsLocalDate
                            dialog.close()
                        }
                    }
                }
            }
            dialog.open()
        }
    }

    fun checkFields(): Boolean {
        var result = true
        listOf(policyNumber, firstName, lastName).forEach {
            if (it.value.isNullOrBlank()) {
                it.errorMessage = FIELD_IS_REQUIRED
                result = false
            }
        }
        return result
    }

    companion object {
        private const val FIELD_IS_REQUIRED = "This field is required for online eligibility verification."
        private val relationshipCodeItems = listOf("SEL", "UNK", "CHD", "SPO", "OTH", "PAR", "DOM")
        private val genderItems = listOf("Male", "Female", "Unknown")
    }
}