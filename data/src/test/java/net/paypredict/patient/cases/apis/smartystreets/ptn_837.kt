package net.paypredict.patient.cases.apis.smartystreets

import com.smartystreets.api.us_street.Lookup
import com.smartystreets.api.us_street.MatchType
import net.paypredict.patient.cases.mongo.DBS
import net.paypredict.patient.cases.mongo.doc
import net.paypredict.patient.cases.mongo.opt
import org.bson.Document

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/29/2018.
 */

fun main(args: Array<String>) {
    val usStreet = UsStreet()
    DBS.ptn().getCollection("ptn_837")
        .find()
        .projection(doc {
            doc["sbrAdr1"] = 1
            doc["sbrAdr2"] = 1
            doc["sbrCity"] = 1
            doc["sbrSt"] = 1
            doc["sbrZIP"] = 1
        })
        .toList()
        .forEachIndexed { index: Int, ptn: Document ->
        val lookup = Lookup().apply {
            match = MatchType.RANGE
            street = ptn.opt("sbrAdr1")
            street2 = ptn.opt("sbrAdr2")
            city = ptn.opt("sbrCity")
            state = ptn.opt("sbrSt")
            zipCode = ptn.opt("sbrZIP")
            print("$index: $street | $street2 | $city | $state | $zipCode")
        }
        usStreet.send(lookup)
        println(": " + (lookup.result ?: arrayListOf()).joinToString { it.analysis.footnotes ?: "Ok" })
    }
}
