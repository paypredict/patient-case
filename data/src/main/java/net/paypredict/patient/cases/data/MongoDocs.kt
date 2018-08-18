package net.paypredict.patient.cases.data

import org.bson.Document
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/14/2018.
 */

fun Document.toCase(): Case =
    net.paypredict.patient.cases.data.Case().also { case ->
        net.paypredict.patient.cases.data.Case::class.memberProperties
            .filterIsInstance<KMutableProperty1<Case, Any>>()
            .forEach { kProperty ->
                val value = this[kProperty.name]
                if (value != null && kProperty.returnType.jvmErasure.isInstance(value))
                    kProperty.set(case, value)
            }
        if (case.req == null) {
            case.req = this["_id"] as? String
        }
    }


fun Case.toDocument(): Document =
    Document().also { document ->
        document.putNN("_id", req)
        Case::class.memberProperties
            .forEach { kProperty ->
                document.putNN(kProperty.name, kProperty.get(this))
            }
    }

private fun Document.putNN(name: String, value: Any?) {
    if (value != null) this[name] = value
}
