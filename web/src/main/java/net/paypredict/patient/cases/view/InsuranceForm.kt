package net.paypredict.patient.cases.view

import com.mongodb.client.FindIterable
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.HasSize
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.ThemableLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.binder.Binder
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.opt
import net.paypredict.patient.cases.data.worklist.Insurance
import org.bson.Document
import kotlin.reflect.KMutableProperty1

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/5/2018.
 */
class InsuranceForm : Composite<VerticalLayout>(), HasSize, ThemableLayout {
    private val ppPayers = PPPayers()

    private var binder: Binder<Insurance> = Binder()

    private val zmPayerId: ComboBox<InsuranceItem?> = ComboBox<InsuranceItem?>("ZirMed Payer").apply {
        width = "100%"
        isRequired = true
        isAllowCustomValue = false
        isPreventInvalidInput = true

        setItemLabelGenerator { it?.displayName }
        addValueChangeListener { event ->
            pokitDokPayer.value = event?.value?.toPokitDokPayer()?.displayName ?: ""
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

    private val pokitDokPayer = TextField("PokitDok Payer").apply {
        width = "100%"
        isReadOnly = true
        suffixComponent = HorizontalLayout().apply {
            isPadding = false
            isSpacing = false
            this += Button(VaadinIcon.EDIT.create()).apply {
                element.setAttribute("theme", "icon small tertiary")
            }
            this += Button(VaadinIcon.QUESTION_CIRCLE.create()).apply {
                element.setAttribute("theme", "icon small tertiary")
            }
        }
    }

    var value: Insurance? = null
        get() = field?.also { res ->
            binder.writeBean(res)
        }
        set(new) {
            zmPayerId.setItems(InsuranceItem.all)
            pokitDokPayer.value = InsuranceItem[new?.zmPayerId]?.toPokitDokPayer()?.displayName ?: ""
            binder.readBean(new)
            field = new
        }

    init {
        content.isPadding = false
        content.isSpacing = false
        content.width = "100%"
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
        content += zmPayerId
        content += pokitDokPayer
    }


    data class InsuranceItem(val zmPayerId: String, val displayName: String) {
        companion object
    }

    private fun InsuranceItem.toPokitDokPayer(): PPPayers.PokitDokPayer? =
        ppPayers.lookupPkdByZmPayerId[zmPayerId]

    private val InsuranceItem.Companion.all: List<InsuranceItem> by lazy {
        ppPayers.zirmedPayers.values.mapNotNull { InsuranceItem[it] }
    }

    private operator fun InsuranceItem.Companion.get(zmPayerId: String?): InsuranceItem? =
        InsuranceItem[ppPayers.zirmedPayers[zmPayerId]]

    private operator fun InsuranceItem.Companion.get(zirMedPayer: PPPayers.ZirMedPayer?): InsuranceItem? =
        zirMedPayer?.run { InsuranceItem(_id, displayName) }


    private class PPPayers {
        data class ZirMedPayer(
            override val _id: String,
            val displayName: String
        ) : Doc

        val zirmedPayers: Map<String, ZirMedPayer> by lazy {
            findAndMap("zirmedPayers") { doc ->
                ZirMedPayer(
                    _id = doc["_id"] as String,
                    displayName = doc.opt<String>("displayName") ?: "???"
                )
            }
        }


        data class MatchPayer(
            override val _id: String,
            val displayName: String?,
            val zmPayerId: String?,
            val zmExtPayerId: String?,
            val pkdExtPayerId: String?
        ) : Doc

        val matchPayers: Map<String, MatchPayer> by lazy {
            findAndMap("matchPayers") { doc ->
                MatchPayer(
                    _id = doc["_id"] as String,
                    displayName = doc.opt<String>("displayName"),
                    zmPayerId = doc.opt<String>("zmPayerId"),
                    zmExtPayerId = doc.opt<String>("zmExtPayerId"),
                    pkdExtPayerId = doc.opt<String>("pkdExtPayerId")
                )
            }
        }
        val matchPayersByZmPayerId: Map<String, MatchPayer> by lazy {
            matchPayers.values.mapNotNull { it.zmPayerId?.let { key -> key to it } }.toMap()
        }
        val matchPayersByZmExtPayerId: Map<String, MatchPayer> by lazy {
            matchPayers.values.mapNotNull { it.zmExtPayerId?.let { key -> key to it } }.toMap()
        }

        data class PokitDokPayer(
            override val _id: String,
            val displayName: String,
            val zmPayerId: String?
        ) : Doc

        val lookupPkd: Map<String, PokitDokPayer> by lazy {
            findAndMap("lookupPkd") { doc ->
                PokitDokPayer(
                    _id = doc["_id"] as String,
                    displayName = doc.opt<String>("displayName") ?: "???",
                    zmPayerId = doc.opt<String>("zmPayerId")
                )
            }
        }

        val lookupPkdByZmPayerId: Map<String, PokitDokPayer> by lazy {
            lookupPkd.values.mapNotNull { it.zmPayerId?.let { key -> key to it } }.toMap()
        }


        private inline fun <reified T : Doc> findAndMap(
            collectionName: String,
            map: (Document) -> T
        ): Map<String, T> =
            mutableMapOf<String, T>().also { result ->
                find(collectionName).forEach { doc ->
                    map(doc).also { result[it._id] = it }
                }
            }

        private fun find(collectionName: String): FindIterable<Document> =
            DBS.ppPayers().getCollection(collectionName).find()

        interface Doc {
            @Suppress("PropertyName")
            val _id: String
        }
    }
}

