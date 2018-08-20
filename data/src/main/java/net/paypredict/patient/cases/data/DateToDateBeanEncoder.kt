package net.paypredict.patient.cases.data

import com.vaadin.flow.templatemodel.ModelEncoder
import net.paypredict.patient.cases.VaadinBean
import java.io.Serializable
import java.util.*


/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */

@VaadinBean
class DateBean : Serializable {
    var day: String? = null
    var month: String? = null
    var year: String? = null
    val str: String get() = "$year-$month-$day"
}

@VaadinBean
class DateTimeBean : Serializable {
    var day: String? = null
    var month: String? = null
    var year: String? = null
    var time24: String? = null
    val str: String get() = "$year-$month-$day $time24"
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

class DateToDateTimeBeanEncoder : ModelEncoder<Date, DateTimeBean> {
    override fun encode(modelValue: Date?): DateTimeBean? {
        if (modelValue == null) {
            return null
        }
        val bean = DateTimeBean()
        val calendar = GregorianCalendar.getInstance()
        calendar.time = modelValue
        val hh = calendar.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val mm = calendar.get(Calendar.MINUTE).toString().padStart(2, '0')
        val ss = calendar.get(Calendar.SECOND).toString().padStart(2, '0')
        bean.time24 = "$hh:$mm:$ss"
        bean.day = calendar.get(Calendar.DAY_OF_MONTH).toString()
        bean.month = (calendar.get(Calendar.MONTH) + 1).toString()
        bean.year = calendar.get(Calendar.YEAR).toString()
        return bean
    }

    override fun decode(presentationValue: DateTimeBean?): Date? {
        if (presentationValue == null) {
            return null
        }
        val year = presentationValue.year?.toInt() ?: 0
        val month = presentationValue.month?.toInt() ?: 0-1
        val day = presentationValue.day?.toInt() ?: 0
        val time24 = (presentationValue.time24 ?: "00:00:00").split(':')
        val hh = time24.getOrNull(0)?.toInt() ?: 0
        val mm = time24.getOrNull(1)?.toInt() ?: 0
        val ss = time24.getOrNull(2)?.toInt() ?: 0
        val calendar = GregorianCalendar.getInstance()
        calendar.set(year, month, day, hh, mm, ss)
        return calendar.time
    }
}
