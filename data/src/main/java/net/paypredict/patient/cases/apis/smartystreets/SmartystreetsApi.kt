package net.paypredict.patient.cases.apis.smartystreets

import com.smartystreets.api.StaticCredentials
import com.smartystreets.api.us_street.Analysis
import com.vaadin.flow.component.JsonSerializable
import elemental.json.Json
import elemental.json.JsonObject
import net.paypredict.patient.cases.VaadinBean
import net.paypredict.patient.cases.data.opt
import org.bson.Document
import java.io.File

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/30/2018.
 */

val smartyStreetsApiCredentials: StaticCredentials by lazy {
    StaticCredentials(FileConfiguration.authId, FileConfiguration.authToken)
}

@VaadinBean
enum class FootNote(val label: String, val note: String) : JsonSerializable {
    A(
        "Corrected ZIP Code",
        """The address was found to have a different 5-digit ZIP Code than given in the submitted list.
            |The correct ZIP Code is shown in the ZIP Code field.""".trimMargin()
    ),
    B(
        "Fixed city/state spelling",
        """The spelling of the city name and/or state abbreviation in the submitted address was found to be
          |different than the standard spelling. The standard spelling of the city name and state abbreviation
          |is shown in the City and State fields.""".trimMargin()
    ),
    C(
        "Invalid city/state/ZIP",
        """The ZIP Code in the submitted address could not be found because neither a valid city and state,
          |nor valid 5-digit ZIP Code was present. SmartyStreets recommends that the customer check the accuracy
          |of the submitted address.""".trimMargin()
    ),
    D(
        "No ZIP+4 assigned",
        """This is a record listed by the United States Postal Service as a non-deliverable location.
          |SmartyStreets recommends that the customer check the accuracy of the submitted address.""".trimMargin()
    ),
    E(
        "Same ZIP for multiple",
        """Multiple records were returned, but each shares the same 5-digit ZIP Code."""
    ),
    F(
        "Address not found",
        """The address, exactly as submitted, could not be found in the city, state, or ZIP Code provided.
          |Many factors contribute to this; either the primary number is missing, the street is missing, or
          |the street is too horribly misspelled to understand.""".trimMargin()
    ),
    G(
        "Used firm data",
        """Information in the firm line was determined to be a part of the address. It was moved out of
          |the firm line and incorporated into the address line.""".trimMargin()
    ),
    H(
        "Missing secondary number",
        """ZIP+4 information indicates that this address is a building. The address as submitted does not
          |contain a secondary (apartment, suite, etc.) number. SmartyStreets recommends that the customer
          |check the accuracy of the submitted address and add the missing secondary number to ensure the
          |correct Delivery Point Barcode (DPBC).""".trimMargin()
    ),
    I(
        "Insufficient/ incorrect address data",
        """More than one ZIP+4 Code was found to satisfy the address as submitted. The submitted address
          |did not contain sufficiently complete or correct data to determine a single ZIP+4 Code. SmartyStreets
          |recommends that the customer check the accuracy and completeness of the submitted address. For example,
          |a street may have a similar address at both the north and south ends of the street.""".trimMargin()
    ),
    J(
        "Dual address",
        """The input contained two addresses. For example: 123 MAIN ST PO BOX 99."""
    ),
    K(
        "Cardinal rule match",
        """Although the address as submitted is not valid, we were able to find a match by changing
          |the cardinal direction (North, South, East, West). The cardinal direction we used to find
          |a match is found in the components.""".trimMargin()
    ),
    L(
        "Changed address component",
        """An address component (i.e., directional or suffix only) was added, changed, or deleted in order to
          |achieve a match.""".trimMargin()
    ),
    LL(
        "Flagged address for LACSLink",
        """The input address matched a record that was LACS-indicated, that was submitted to LACSLink for
          |processing. This does not mean that the address was converted; it only means that the address was
          |submitted to LACSLink because the input address had the LACS indicator set.""".trimMargin()
    ),
    LI(
        LL.label,
        LL.note
    ),
    M(
        "Fixed street spelling",
        """The spelling of the street name was changed in order to achieve a match."""
    ),
    N(
        "Fixed abbreviations",
        """The delivery address was standardized. For example, if STREET was in the delivery address,
          |SmartyStreets will return ST as its standard spelling.""".trimMargin()
    ),
    O(
        "Multiple ZIP+4; lowest used",
        """More than one ZIP+4 Code was found to satisfy the address as submitted. The lowest ZIP+4
          |add-on may be used to break the tie between the records.""".trimMargin()
    ),
    P(
        "Better address exists",
        """The delivery address is matchable, but it is known by another (preferred) name. For example,
          |in New York, NY, AVENUE OF THE AMERICAS is also known as 6TH AVE. An inquiry using a delivery
          |address of 39 6th Avenue would be flagged with Footnote P.""".trimMargin()
    ),
    Q(
        "Unique ZIP match",
        """Match to an address with a unique ZIP Code"""
    ),
    R(
        "No match; EWS: Match soon",
        """The delivery address is not yet matchable, but the Early Warning System file indicates that
          |an exact match will be available soon.""".trimMargin()
    ),
    S(
        "Bad secondary address",
        """The secondary information (apartment, suite, etc.) does not match that on the national ZIP+4 file.
          |The secondary information, although present on the input address, was not valid in the range found
          |on the national ZIP+4 file.""".trimMargin()
    ),
    T(
        "Multiple response due to magnet street syndrome",
        """The search resulted in a single response; however, the record matched was flagged as having magnet
          |street syndrome, and the input street name components (pre-directional, primary street name,
          |post-directional, and suffix) did not exactly match those of the record. A "magnet street" is one
          |having a primary street name that is also a suffix or directional word, having either a post-directional
          |or a suffix (i.e., 2220 PARK MEMPHIS TN logically matches to a ZIP+4 record 2200-2258 PARK AVE MEMPHIS
          |TN 38114-6610), but the input address lacks the suffix "AVE" which is present on the ZIP+4 record.
          |The primary street name "PARK" is a suffix word. The record has either a suffix or a post-directional
          |present. Therefore, in accordance with CASS requirements, a ZIP+4 Code must not be returned.
          |The multiple response return code is given since a "no match" would prevent the best candidate.
          |""".trimMargin()
    ),
    U(
        "Unofficial post office name",
        """The city or post office name in the submitted address is not recognized by the United States Postal
          |Service as an official last line name (preferred city name), and is not acceptable as an alternate
          |name. The referred city name is included in the City field.""".trimMargin()
    ),
    V(
        "Unverifiable city / state",
        """The city and state in the submitted address could not be verified as corresponding to the given 5-digit
          |ZIP Code. This comment does not necessarily denote an error; however, SmartyStreets recommends that the
          |customer check the accuracy of the city and state in the submitted address.""".trimMargin()
    ),
    W(
        "Invalid delivery address",
        """The input address record contains a delivery address other than a PO Box, General Delivery, or Postmaster
          |5-digit ZIP Code that is identified as a "small town default." The USPS does not provide street delivery
          |service for this ZIP Code. The USPS requires the use of a PO Box, General Delivery, or Postmaster for
          |delivery within this ZIP Code.""".trimMargin()
    ),
    X(
        "Unique ZIP Code",
        """Default match inside a unique ZIP Code"""
    ),
    Y(
        "Military match",
        """Match made to a record with a military or diplomatic ZIP Code."""
    ),
    Z(
        "Matched with ZIPMOVE",
        """The ZIPMOVE product shows which ZIP+4 records have moved from one ZIP Code to another. If an input address
          |matches a ZIP+4 record which the ZIPMOVE product indicates has moved, the search is performed again in the
          |new ZIP Code.""".trimMargin()
    ),
    NONE("Not a footnote", """""");

    override fun toJson(): JsonObject =
        Json.createObject().also { json ->
            json.put("name", name)
            json.put("label", label)
            json.put("note", note)
        }

    override fun readJson(value: JsonObject): FootNote =
        value.getString("name")?.let { FootNote.valueOf(it) } ?: NONE

    companion object {
        @Suppress("unused")
        fun encodeFootNoteSet(footNoteSet: FootNoteSet): String? =
            footNoteSet.joinToString(separator = "") { it.name + "#" }

        fun decodeFootNoteSet(string: String?): FootNoteSet =
            string
                ?.splitToSequence('#')
                ?.mapNotNull {
                    try {
                        FootNote.valueOf(it)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
                ?.toSet()
                ?: emptySet()
    }
}

typealias FootNoteSet = Set<FootNote>

val Analysis.footNoteSet: FootNoteSet
    get() = FootNote.decodeFootNoteSet(footnotes)

private object FileConfiguration {
    private val conf: Document by lazy {
        val file = File("/PayPredict/conf/smartystreets.api.json")
        if (file.exists()) Document.parse(file.readText()) else Document()
    }

    val authId: String? by lazy { conf.opt<String>("authId") }
    val authToken: String? by lazy { conf.opt<String>("authToken") }
}