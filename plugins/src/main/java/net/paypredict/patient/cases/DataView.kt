package net.paypredict.patient.cases

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class DataView(
    val caption: String,
    val description: String = "",
    val section: String = ""
)