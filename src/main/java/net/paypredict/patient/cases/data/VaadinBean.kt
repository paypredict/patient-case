package net.paypredict.patient.cases.data

import com.vaadin.flow.templatemodel.ModelEncoder
import java.io.Serializable
import java.util.*


/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */

annotation class VaadinBean

@VaadinBean
class DateBean : Serializable {
    var day: String? = null
    var month: String? = null
    var year: String? = null
    val str: String get() = "$year-$month-$day"
}

class DateToDateBeanEncoder : ModelEncoder<Date, DateBean> {
    override fun encode(modelValue: Date?): DateBean? {
        if (modelValue == null) {
            return null
        }
        val bean = DateBean()
        val calendar = GregorianCalendar.getInstance()
        calendar.time = modelValue
        bean.day = calendar.get(Calendar.DAY_OF_MONTH).toString()
        bean.month = (calendar.get(Calendar.MONTH) + 1).toString()
        bean.year = calendar.get(Calendar.YEAR).toString()
        return bean
    }

    override fun decode(presentationValue: DateBean?): Date? {
        if (presentationValue == null) {
            return null
        }
        val year = presentationValue.year?.toInt() ?: 0
        val day = presentationValue.day?.toInt() ?: 0
        val month = presentationValue.month?.toInt() ?: 0-1
        val calendar = GregorianCalendar.getInstance()
        calendar.set(year, month, day)
        return calendar.time
    }
}