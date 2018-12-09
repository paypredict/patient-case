package net.paypredict.patient.cases.view

import com.vaadin.flow.component.ComponentEventListener
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.Key
import com.vaadin.flow.component.KeyPressEvent
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.datepicker.DatePicker
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import net.paypredict.patient.cases.data.worklist.formatAs
import net.paypredict.patient.cases.mongo.*
import org.bson.Document
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 12/4/2018.
 */
class CaseSearchForm(
    searchParameters: SearchParameters? = null,
    val onCancel: () -> Unit,
    val onFound: (SearchResult) -> Unit
) : Composite<VerticalLayout>() {

    private val patientName: TextField = textField("Patient Name Contains", searchParameters?.patientName)
    private val serviceDate: DatePicker =
        DatePicker("Date of Service", searchParameters?.serviceDate).apply { width = "100%" }
    private val payerOriginal: TextField = textField("Original Payer Contains", searchParameters?.payerOriginal)
    private val payerFinal: TextField = textField("Final Payer Contains", searchParameters?.payerFinal)
    private val accession: TextField = textField("Accession Contains", searchParameters?.accession)

    private fun textField(label: String, initialValue: String?): TextField =
        TextField(label, initialValue ?: "", "")
            .apply { width = "100%" }

    init {
        content.isPadding = false

        content += H2("Search current and historic cases")
        content += VerticalLayout(
            patientName,
            serviceDate,
            payerOriginal,
            payerFinal,
            accession
        ).apply {
            isPadding = false
            val search = ComponentEventListener<KeyPressEvent?> { search() }
            components.forEach {
                when (it) {
                    is TextField -> it.addKeyPressListener(Key.ENTER, search)
                }
            }
        }

        val bottom = HorizontalLayout(
            Span("Result limited by 50 records"),
            Button("Cancel") { onCancel() },
            Button("Search") { search() }
        ).apply {
            isPadding = false
            defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
        }

        content += bottom
        content.setHorizontalComponentAlignment(FlexComponent.Alignment.END, bottom)
    }

    private sealed class Expr {
        abstract val name: String
        abstract fun matches(doc: Document): Boolean

        class PatientName(val text: String) : Expr() {
            override val name: String = Companion.name

            override fun matches(doc: Document): Boolean {
                val value: String =
                    doc.opt<String>("case", "Case", "Patient", "name") ?: return false
                return value.contains(text, ignoreCase = true)
            }

            companion object {
                const val name = "case.Case.Patient.name"

                fun parse(text: String?): PatientName? {
                    if (text.isNullOrBlank()) return null
                    return PatientName(text)
                }
            }
        }

        class ServiceDate(val text: String) : Expr() {
            override val name: String = Companion.name

            override fun matches(doc: Document): Boolean =
                doc.opt<List<*>>("case", "Case", "SuperBillDetails", "SuperBill")
                    ?.asSequence()
                    ?.mapNotNull { (it as? Document)?.opt<String>("serviceDate") }
                    ?.any { it.contains(text, ignoreCase = true) }
                    ?: false

            companion object {
                const val name = "case.Case.SuperBillDetails.SuperBill.serviceDate"
                private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")

                fun parse(date: LocalDate?): ServiceDate? {
                    if (date == null) return null
                    return ServiceDate(date formatAs dateFormat)
                }
            }
        }

        class PayerOriginal(val text: String) : Expr() {
            override val name: String = Companion.name

            override fun matches(doc: Document): Boolean =
                doc.opt<List<*>>("case", "Case", "SubscriberDetails", "Subscriber")
                    ?.asSequence()
                    ?.mapNotNull { (it as? Document)?.opt<String>("payerName") }
                    ?.any { it.contains(text, ignoreCase = true) }
                    ?: false

            companion object {
                const val name = "case.Case.SubscriberDetails.Subscriber.payerName"

                fun parse(text: String?): PayerOriginal? {
                    if (text.isNullOrBlank()) return null
                    return PayerOriginal(text)
                }
            }
        }

        class PayerFinal(val id: Set<String>) : Expr() {
            override val name: String = Companion.name

            override fun matches(doc: Document): Boolean {
                val value: String =
                    doc.opt("case", "Case", "SubscriberDetails", "Subscriber", "payerId") ?: return false
                return value in id
            }

            companion object {
                const val name = "case.Case.SubscriberDetails.Subscriber.payerId"

                fun parse(text: String?): PayerFinal? {
                    if (text.isNullOrBlank()) return null
                    return null // TODO
                }
            }
        }

        class Accession(val text: String) : Expr() {
            override val name: String = Companion.name

            override fun matches(doc: Document): Boolean {
                val value: String =
                    doc.opt("case", "Case", "accessionNumber") ?: return false
                return value.contains(text, ignoreCase = true)
            }

            companion object {
                const val name = "case.Case.accessionNumber"

                fun parse(text: String?): Accession? {
                    if (text.isNullOrBlank()) return null
                    return Accession(text)
                }
            }
        }
    }

    private fun search() {
        val searchParameters =
            SearchParameters(
                patientName = patientName.value,
                serviceDate = serviceDate.value,
                payerOriginal = payerOriginal.value,
                payerFinal = payerFinal.value,
                accession = accession.value
            )

        val expressions: List<Expr> =
            listOfNotNull(
                Expr.Accession.parse(searchParameters.accession),
                Expr.PatientName.parse(searchParameters.patientName),
                Expr.ServiceDate.parse(searchParameters.serviceDate),
                Expr.PayerOriginal.parse(searchParameters.payerOriginal),
                Expr.PayerFinal.parse(searchParameters.payerFinal)
            )

        val ids: List<String> =
            if (expressions.isNotEmpty())
                cases()
                    .find()
                    .projection(doc { expressions.forEach { self[it.name] = 1 } })
                    .sort(doc { self["doc.created"] = -1 })
                    .filter { doc -> expressions.all { it.matches(doc) } }
                    .map { it["_id"] as String }
                    .take(50)
            else
                emptyList()

        onFound(
            SearchResult(
                size = ids.size,
                filter = doc { self["_id"] = doc { self[`$in`] = ids } },
                parameters = searchParameters
            )
        )
    }

    companion object {
        private var firstTime = true

        private fun cases(): DocumentMongoCollection =
            DBS.Collections
                .cases()
                .also {
                    if (firstTime) {
                        firstTime = false
                        it.createIndex(doc { self["doc.created"] = 1 })
                    }
                }
    }
}

data class SearchParameters(
    val patientName: String? = null,
    val serviceDate: LocalDate? = null,
    val payerOriginal: String? = null,
    val payerFinal: String? = null,
    val accession: String? = null
)

data class SearchResult(
    val size: Int,
    val filter: Document,
    val parameters: SearchParameters? = null
)
