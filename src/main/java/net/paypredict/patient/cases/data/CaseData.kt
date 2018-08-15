package net.paypredict.patient.cases.data

import com.vaadin.flow.templatemodel.Encode
import net.paypredict.patient.cases.DataView
import net.paypredict.patient.cases.dataViewMap
import java.util.*

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/8/2018.
 *
 * [based on requisition form](http://host.simagis.com/live/layers/view.jsp?name=Users%2Fvk%2F166614c4-52ab-4d14-9d09-26f83b0ee7c0)
 */
@VaadinBean
data class Case(
    @DataView("Last Name", "Patient's last name")
    var ptnLast: String? = null,

    @DataView("First Name", "Patient's first name")
    var ptnFirst: String? = null,

    @DataView("MI", "Patient's MI")
    var ptnMI: String? = null,

    @DataView("Gender", "Patient's gender")
    var ptnG: String? = null,

    @set:Encode(DateToDateBeanEncoder::class)
    @DataView("DBD", "Patient's date of birth")
    var ptnBd: Date? = null,

    @DataView("SSN", "Patient's SSN")
    var ptnSSN: String? = null,

    @DataView("Address", "Patient's address", "Patient Information")
    var ptnAddrAll: String? = null,

    @DataView("Address: line 1", "Patient's address: line 1", "Patient Information")
    var ptnAdr1: String? = null,

    @DataView("Address: line 2", "Patient's address: line 2", "Patient Information")
    var ptnAdr2: String? = null,

    @DataView("Address: City", "Patient's address: City", "Patient Information")
    var ptnCity: String? = null,

    @DataView("Address: Street", "Patient's address: Street", "Patient Information")
    var ptnSt: String? = null,

    @DataView("Address: ZIP", "Patient's address: ZIP", "Patient Information")
    var ptnZIP: String? = null,

    @DataView("Account", "Client Account")
    var clAcc: String? = null,

    @DataView("Physician", "Ordering Physician")
    var npi: String? = null,

    @DataView("Requisition", "Accession Unique ID")
    var req: String? = null,

    @set:Encode(DateToDateBeanEncoder::class)
    @DataView("Date", "Collection Date/Time")
    var srvDate: Date? = null,


    @DataView("Address", "Address of Responsible party", "Responsible Party")
    var sbrAddrAll: String? = null,

    @DataView("Address: line 1", "Address of Responsible party: line 1", "Responsible Party")
    var sbrAdr1: String? = null,

    @DataView("Address: line 2", "Address of Responsible party: line 2", "Responsible Party")
    var sbrAdr2: String? = null,

    @DataView("Address: City", "Address of Responsible party: City", "Responsible Party")
    var sbrCity: String? = null,

    @DataView("Address: Street", "Address of Responsible party: Street", "Responsible Party")
    var sbrSt: String? = null,

    @DataView("Address: ZIP", "Address of Responsible party: ZIP", "Responsible Party")
    var sbrZIP: String? = null,

    @DataView("ICD", "ICD Code(s) (indicative)")
    var dxV: List<String> = emptyList(),

    @DataView("Primary Insurance", "Primary Insurance: Insurance company / HMO Name", "Billing Information")
    var prn: String? = null,

    @DataView("Policy #", "Primary Insurance: Policy #", "Billing Information")
    var sbrPolicyId: String? = null,

    @DataView("Group #", "Primary Insurance: Group #", "Billing Information")
    var sbrGroupName: String? = null,

    @DataView("Relation to patient", "Primary Insurance: Relation to patient", "Billing Information")
    var sbrRel: String? = null,

    @DataView("Plan Code", "Primary Insurance: Plan Code", "Billing Information")
    var sbrfCode: String? = null,

    @DataView("Last Name", "Primary Insurance: Holder's last name", "Billing Information")
    var sbrLast: String? = null,

    @DataView("First Name", "Primary Insurance: Holder's first name", "Billing Information")
    var sbrFirst: String? = null,

    @DataView("MI", "Primary Insurance: Holder's MI", "Billing Information")
    var sbrMI: String? = null,

    @DataView("URL", "Accession URL in LIS")
    var acnURL: String? = null,

    @DataView("Attachments", "Attachment IDs to form URL in LIS for opening the accession")
    var atchID: List<String> = emptyList()
)


val caseDataViewMap: Map<String, DataView> by lazy { dataViewMap<Case>() }
