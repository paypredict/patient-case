package net.paypredict.patient.cases.apis.smartystreets

import com.smartystreets.api.us_street.Lookup
import com.smartystreets.api.us_street.MatchType

/**
 *
 *
 * Created by alexei.vylegzhanin@gmail.com on 9/29/2018.
 */
object UsStreetTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val usStreet = UsStreet()
        usStreet.send(Lookup().apply {
            match = MatchType.RANGE
            street = "1600 Amphitheatre Pkwy"
            city = "Mountain View1"
            state = "CA"
        })

        usStreet.send(Lookup().apply {
            match = MatchType.INVALID
            street = "1600 Amphitheatre Pkwy"
            city = "Mountain View1"
            state = "CA"
        })
    }
}