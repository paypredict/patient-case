package net.paypredict.patient.cases.view

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.Label
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.binder.Binder
import net.paypredict.patient.cases.data.worklist.Insurance
import net.paypredict.patient.cases.pokitdok.eligibility.PayersData
import kotlin.reflect.KMutableProperty1

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/5/2018.
 */
class InsuranceForm(header: Component? = null) : Composite<VerticalLayout>(), HasSize, ThemableLayout {

    private val payersData = PayersData()

    private var binder: Binder<Insurance> = Binder()

    private val payerName = Span()

    private val zmPayerId: ComboBox<InsuranceItem?> = ComboBox<InsuranceItem?>("ZirMed Payer").apply {
        width = "100%"
        isRequired = true
        isAllowCustomValue = false
        isPreventInvalidInput = true

        setItemLabelGenerator { it?.displayName }
        addValueChangeListener { event ->
            pokitDokPayer.setPokitDokPayer(event?.value?.toTradingPartner())
        }

        binder
            .forField(this)
            .bind(
                { insurance: Insurance ->
                    InsuranceItem[insurance.zmPayerId]
                },
                { insurance: Insurance, value ->
                    insurance.zmPayerId = value?.zmPayerId
                    insurance.zmPayerName = value?.displayName
                }
            )
    }

    private val pokitDokPayer = TextField("PokitDok Payer").also { field ->
        field.width = "100%"
        field.isReadOnly = true
        field.suffixComponent = HorizontalLayout().apply {
            isPadding = false
            isSpacing = false
            fun Button.withStyle(): Button = apply {
                style["padding"] = "0"
                style["color"] = "var(--lumo-contrast-60pct)"
                element.setAttribute("theme", "icon small contrast tertiary")
            }
            this += Button(VaadinIcon.CLOSE.create()).withStyle().apply {
                addClickListener {
                    val zmPayerIdStr = zmPayerId.value?.zmPayerId
                    if (zmPayerIdStr != null) {
                        payersData.removeUsersMatchPayersRecord(zmPayerIdStr)
                        field.setPokitDokPayer(value?.toTradingPartner())
                    } else {
                        zmPayerId.focus()
                        field.prefixComponent = errorLabel("ZirMed Payer Required")
                    }
                }
            }
            this += Button(VaadinIcon.EDIT.create()).withStyle().apply {
                addClickListener {
                    if (zmPayerId.value?.zmPayerId != null) {
                        selectPokitDokPayer()
                    } else {
                        zmPayerId.focus()
                        field.prefixComponent = errorLabel("ZirMed Payer Required")
                    }
                }
            }
        }
    }

    var caseId: String? = null

    var value: Insurance? = null
        get() = (field ?: Insurance()).also { res ->
            binder.writeBeanIfValid(res)
        }
        set(new) {
            payerName.text = new?.payerName ?: ""
            zmPayerId.setItems(InsuranceItem.all)
            pokitDokPayer.setPokitDokPayer(new?.toTradingPartner())
            binder.readBean(new)
            field = new
        }

    val isValid: Boolean
        get() = binder.validate().isOk

    init {
        content.isPadding = false
        content.isSpacing = false
        content.width = "100%"

        content += HorizontalLayout().apply {
            isPadding = false
            style["flex-wrap"] = "wrap"
            defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
            if (header != null) this += header
            this += payerName
        }

        if (false) {
            content += Div().apply {
                width = "100%"
                style["display"] = "inline-flex"
                style["flex-wrap"] = "wrap"
                fun KMutableProperty1<Insurance, String?>.bindTextView(label: String, marginRight: String? = "1em") =
                    TextField(label).apply {
                        binder.forField(this).bind(getter, setter)
                        isReadOnly = true
                        style["max-width"] = "100%"
                        style["margin-right"] = marginRight
                    }
                add(
                    Insurance::payerName.bindTextView("Payer Name").apply { style["flex-grow"] = "100" },
                    Insurance::typeCode.bindTextView("Type"),
                    Insurance::payerId.bindTextView("Payer ID"),
                    Insurance::planCode.bindTextView("Plan Code", marginRight = null)
                )
            }
        }
        content += zmPayerId
        content += pokitDokPayer
    }


    data class InsuranceItem(val zmPayerId: String, val displayName: String) {
        companion object
    }

    private fun InsuranceItem.toTradingPartner(): PayersData.TradingPartner? =
        payersData.tradingPartners[payersData.findPkdPayerId(zmPayerId)]

    private fun Insurance.toTradingPartner(): PayersData.TradingPartner? =
        payersData.tradingPartners[payersData.findPkdPayerId(zmPayerId)]

    private val InsuranceItem.Companion.all: List<InsuranceItem> by lazy {
        payersData.zirmedPayers.values.mapNotNull { InsuranceItem[it] }
    }

    private operator fun InsuranceItem.Companion.get(zmPayerId: String?): InsuranceItem? =
        InsuranceItem[payersData.zirmedPayers[zmPayerId]]

    private operator fun InsuranceItem.Companion.get(zirMedPayer: PayersData.ZirMedPayer?): InsuranceItem? =
        zirMedPayer?.run { InsuranceItem(_id, displayName) }


    private fun selectPokitDokPayer() {
        Dialog().also { dialog ->
            dialog.width = "70vw"
            dialog += VerticalLayout().apply {
                isPadding = false
                val header = H2("Select PokitDok Payer")
                val grid = PokitDokPayerGrid().apply { width = "100%" }
                val actions = HorizontalLayout().apply {
                    isPadding = false
                    this += Button("Cancel") { dialog.close() }
                    this += Button("Select").apply {
                        element.setAttribute("theme", "primary")
                        addClickListener {
                            val zmPayerId = zmPayerId.value?.zmPayerId
                            if (zmPayerId != null) {
                                val selected = grid.value
                                payersData.updateUsersMatchPayersRecord(
                                    zmPayerId = zmPayerId,
                                    pkdPayerId = selected?._id
                                )
                                pokitDokPayer.setPokitDokPayer(value?.toTradingPartner())
                                dialog.close()
                            }
                        }
                    }
                }

                this += header
                this += grid
                this += actions
                setHorizontalComponentAlignment(FlexComponent.Alignment.END, actions)

                grid.selectById(value?.toTradingPartner()?._id)
            }
            dialog.open()
        }
    }

    private fun TextField.setPokitDokPayer(tradingPartner: PayersData.TradingPartner?) {
        value = tradingPartner?.displayName ?: ""
        prefixComponent = null
    }


    private fun errorLabel(text: String) = Label(text).apply { style["color"] = "red" }
}

