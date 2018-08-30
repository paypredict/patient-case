package net.paypredict.patient.cases.apis.pipl

import com.pipl.api.data.Utils
import com.pipl.api.data.containers.Person
import com.pipl.api.data.fields.*
import com.pipl.api.search.SearchAPIRequest
import net.paypredict.patient.cases.data.DBS
import net.paypredict.patient.cases.data.doc
import org.bson.Document
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

/**
 *
 * Created by alexei.vylegzhanin@gmail.com on 8/30/2018.
 */
object TestAddress {
    @JvmStatic
    fun main(args: Array<String>) {
        val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
        val collection = DBS.ptn().getCollection("piplTestAddress")
        val lines = File(args.last()).readLines()
        val headers = lines
            .first()
            .split('\t')
            .mapIndexed { index, line -> line to index }
            .toMap()

        operator fun List<String>.get(name: String): String? {
            val index = headers[name]!!
            return this[index].let {
                when {
                    it == """""""" -> null
                    it.isNotBlank() -> it
                    else -> null
                }
            }
        }

        lines.drop(1).forEach { line ->
            val record = line.split('\t')

            val dateOfBirth = record["dateOfBirth"]

            val person = Person(
                mutableListOf<Field>().also { fields ->
                    fields += Address.Builder()
                        .country("US")
                        .state(record["state"])
                        .city(record["city"])
                        .zipCode(record["zip"])
                        .street(listOfNotNull(record["address1"], record["address2"]).joinToString())
                        .build()
                    fields += Name.Builder()
                        .first(record["firstName"])
                        .last(record["lastName"])
                        .middle(record["middleInitials"])
                        .build()
                    record["gender"]?.let { gender ->
                        fields += Gender(enumValueOf(gender.toLowerCase()))
                    }
                    dateOfBirth?.let { dob ->
                        val dobDate =
                            Date.from(LocalDate.parse(dob, dateFormat).atStartOfDay(ZoneOffset.UTC).toInstant())
                        fields += DOB(DateRange(dobDate, dobDate))
                    }
                }
            )
            val t0 = System.currentTimeMillis()
            val response =
                SearchAPIRequest(person, piplApiSearchConfiguration).send()
            val t1 = System.currentTimeMillis()

            collection.insertOne(doc {
                doc["line"] = line
                doc["dateTime"] = Date()
                doc["apiTimeMs"] = (t1 - t0).toInt()
                doc["person"] = Document.parse(Utils.toJson(person))
                doc["response"] = Document.parse(response.json)
            })

        }
    }
}