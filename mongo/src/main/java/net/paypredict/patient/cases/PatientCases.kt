package net.paypredict.patient.cases

import java.io.File

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
object PatientCases {
    var client = "test"
    val clientDir get() = File("/PayPredict/clients/$client")
}
