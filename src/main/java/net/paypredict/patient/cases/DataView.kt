package net.paypredict.patient.cases

import kotlin.reflect.full.memberProperties

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

inline fun <reified T : Any> dataViewMap(): Map<String, DataView> =
    T::class
        .memberProperties
        .mapNotNull { property ->
            property.annotations
                .filterIsInstance<DataView>()
                .firstOrNull()
                ?.let { property.name to it }
        }
        .toMap()
