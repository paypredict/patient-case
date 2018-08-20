package net.paypredict.patient.cases

import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/18/2018.
 */
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

data class MetaData<T>(
    val view: DataView,
    val prop: KProperty1<T, *>
)

inline fun <reified T : Any> metaDataMap(): Map<String, MetaData<T>> =
    T::class
        .memberProperties
        .mapNotNull { property: KProperty1<T, *> ->
            property.annotations
                .filterIsInstance<DataView>()
                .firstOrNull()
                ?.let { property.name to MetaData(it, property) }
        }
        .toMap()
