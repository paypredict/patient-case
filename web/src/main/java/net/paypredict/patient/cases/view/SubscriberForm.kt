package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.H3
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
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.mongo.opt
import net.paypredict.patient.cases.data.worklist.Person
import net.paypredict.patient.cases.data.worklist.Subscriber
import net.paypredict.patient.cases.data.worklist.asLocalDateOrNull
import net.paypredict.patient.cases.data.worklist.formatAs
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
        setItems()
        binder
            .forField(this)
            .bind(
                Subscriber::relationshipCode.getter,
                Subscriber::relationshipCode.setter
            )
    }
    private val genderItems = listOf("Male", "Female", "Unknown")
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

    var caseId: String? = null
    var value: Subscriber? = null
        get() {
            if (field == null) field = Subscriber()
            binder.writeBeanIfValid(field)
            return field
        }
        set(new) {
            relationshipCode.setItems(listOfNotNull(new?.relationshipCode))
            gender.setItems((listOfNotNull(new?.gender) + genderItems).toSet())
            binder.readBean(new)
            field = new
        }

    val isValid: Boolean
        get() = binder.validate().isOk

    init {
        content += relationshipCode
        content += HorizontalLayout().apply {
            element.setAttribute("colspan", "4")
            isPadding = false
            defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
            this += Button("Copy from Patient").apply {
                element.setAttribute("theme", "tertiary-inline")
                addClickListener {
                    showCopyFromPatientDialog()
                }
            }
        }
        content += firstName
        content += lastName
        content += mi
        content += gender
        content += dob
        content += policyNumber

        content.setResponsiveSteps(
            FormLayout.ResponsiveStep("10em", 1),
            FormLayout.ResponsiveStep("20em", 2),
            FormLayout.ResponsiveStep("30em", 3),
            FormLayout.ResponsiveStep("40em", 4),
            FormLayout.ResponsiveStep("50em", 5)
        )
    }

    private fun showCopyFromPatientDialog() {
        val case = DBS.Collections.casesRaw().find(doc {
            doc["_id"] = caseId
        }).firstOrNull()
        val patient = case?.opt<Document>("case", "Case", "Patient")
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
                            firstName.value = patient.opt("firstName") ?: ""
                            lastName.value = patient.opt("lastName") ?: ""
                            mi.value = patient.opt("middleInitials") ?: ""
                            gender.value = patient.opt("gender") ?: ""
                            dob.value = patient.opt<String>("dateOfBirth") asLocalDateOrNull  Person.dateFormat
                            dialog.close()
                        }
                    }
                }
            }
            dialog.open()
        }
    }
}