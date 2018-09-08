package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.binder.Binder
import com.vaadin.flow.data.binder.ValidationResult
import com.vaadin.flow.data.binder.ValueContext
import net.paypredict.patient.cases.data.worklist.Subscriber
import net.paypredict.patient.cases.data.worklist.formatAs
import java.time.LocalDate
import kotlin.properties.Delegates

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
        isRequired = true
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

    var value: Subscriber? = null
        get() = field?.also { res ->
            binder.writeBean(res)
        }
        set(new) {
            relationshipCode.setItems(listOfNotNull(new?.relationshipCode))
            gender.setItems((listOfNotNull(new?.gender) + genderItems).toSet())
            binder.readBean(new)
            field = new
        }

    init {
        content += relationshipCode
        content += HorizontalLayout().apply {
            element.setAttribute("colspan", "4")
            isPadding = false
            defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
            this += Button("Copy from Patient").apply {
                element.setAttribute("theme", "tertiary-inline")
            }
        }
        content += TextField("First Name").apply {
            isRequired = true
            binder
                .forField(this)
                .withValidator(fieldIsRequired)
                .bind(
                    Subscriber::firstName.getter,
                    Subscriber::firstName.setter
                )
        }
        content += TextField("Last Name").apply {
            isRequired = true
            binder
                .forField(this)
                .withValidator(fieldIsRequired)
                .bind(
                    Subscriber::lastName.getter,
                    Subscriber::lastName.setter
                )
        }
        content += gender
        content += DatePicker("Date Of Birth").apply {
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
        content += TextField("Policy #").apply {
            isRequired = true
            binder
                .forField(this)
                .withValidator(fieldIsRequired)
                .bind(
                    Subscriber::policyNumber.getter,
                    Subscriber::policyNumber.setter
                )
        }

        content.setResponsiveSteps(
            FormLayout.ResponsiveStep("10em", 1),
            FormLayout.ResponsiveStep("20em", 2),
            FormLayout.ResponsiveStep("30em", 3),
            FormLayout.ResponsiveStep("40em", 4),
            FormLayout.ResponsiveStep("50em", 5)
        )
    }

}