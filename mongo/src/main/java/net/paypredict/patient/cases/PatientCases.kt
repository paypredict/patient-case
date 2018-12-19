package net.paypredict.patient.cases

import java.io.File

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
object PatientCases {
    lateinit var client: String
    val clientDir: File get() = File("/PayPredict/clients/$client").absoluteFile
}
