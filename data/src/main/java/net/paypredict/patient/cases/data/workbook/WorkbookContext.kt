package net.paypredict.patient.cases.data.workbook

import org.apache.poi.ss.usermodel.BuiltinFormats
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Workbook

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 12/13/2018.
 */
class WorkbookContext(private val workbook: Workbook) {

    val headerStyle: CellStyle by lazy {
        workbook.createCellStyle().apply {
            setFont(workbook.createFont().apply { bold = true })
        }
    }

    val dateStyle: CellStyle by lazy {
        workbook.createCellStyle().apply {
            dataFormat = BuiltinFormats.getBuiltinFormat("d-mmm-yy").toShort()
        }
    }

    val dateTimeStyle: CellStyle by lazy {
        workbook.createCellStyle().apply {
            dataFormat = BuiltinFormats.getBuiltinFormat("m/d/yy h:mm").toShort()
        }
    }

}