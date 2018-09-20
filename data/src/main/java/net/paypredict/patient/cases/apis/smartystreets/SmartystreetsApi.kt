package net.paypredict.patient.cases.apis.smartystreets

import com.smartystreets.api.StaticCredentials
import com.smartystreets.api.us_street.Analysis
import net.paypredict.patient.cases.VaadinBean
import net.paypredict.patient.cases.data.opt
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.bson.Document
import java.io.File

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/30/2018.
 */

val smartyStreetsApiCredentials: StaticCredentials by lazy {
    StaticCredentials(Conf.authId, Conf.authToken)
}

@VaadinBean
class FootNote(
    val name: String,
    @Suppress("unused") val label: String,
    @Suppress("unused") val note: String,
    @Suppress("unused") val level: Level = Level.WARNING
) {

    enum class Level : Comparable<Level> {
        INFO,
        WARNING,
        ERROR
    }

    companion object {
        @Suppress("unused")
        fun encodeFootNoteSet(footNoteSet: FootNoteSet): String? =
            footNoteSet.joinToString(separator = "") { it.name + "#" }

        fun decodeFootNoteSet(string: String?): FootNoteSet =
            string
                ?.splitToSequence('#')
                ?.mapNotNull {
                    try {
                        footNoteMap[it] ?: if (it.isNotBlank())
                            FootNote(it, "Unknown footnote $it#", "Unknown footnote $it#", Level.ERROR) else
                            null
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
                ?.toSet()
                ?: emptySet()
    }
}

val footNoteMap: Map<String, FootNote> by lazy {
    mutableMapOf<String, FootNote>()
        .also { map ->
            CSVParser.parse(
                Conf::class.java.getResourceAsStream("dic_footnotes.csv")
                    .reader().use { it.readText().removePrefix("\uFEFF").reader() },
                CSVFormat.EXCEL.withHeader()
            ).use { parser ->
                for (record in parser) {
                    val name = record["Value"] ?: continue
                    val label = record["Definition"] ?: continue
                    val note = record["Details"] ?: continue
                    val level = record["Level"]?.toUpperCase() ?: FootNote.Level.WARNING.name
                    map[name] = FootNote(
                        name = name,
                        label = label,
                        note = note,
                        level = try {
                            FootNote.Level.valueOf(level)
                        } catch (e: Exception) {
                            FootNote.Level.WARNING
                        }
                    )
                }
            }
        }
}

typealias FootNoteSet = Set<FootNote>

val Analysis.footNoteSet: FootNoteSet
    get() = FootNote.decodeFootNoteSet(footnotes)

private object Conf {
    private val conf: Document by lazy {
        val file = File("/PayPredict/conf/smartystreets.api.json")
        if (file.exists()) Document.parse(file.readText()) else Document()
    }

    val authId: String? by lazy { conf.opt<String>("authId") }
    val authToken: String? by lazy { conf.opt<String>("authToken") }
}