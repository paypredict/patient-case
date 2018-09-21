package net.paypredict.patient.cases.data.worklist

import net.paypredict.patient.cases.PatientCases
import java.io.File

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/21/2018.
 */


data class RequisitionForm(val fileName: String)

fun RequisitionForm.file(): File? {
    val file = requisitionFormsDir.resolve(fileName)
    if (file.isFile) return file
    val message = requisitionFormsDir.resolve(".not-found.pdf")
    if (message.isFile) return message
    return null
}

val requisitionFormsDir: File
    get() = PatientCases.clientDir.resolve("requisitionForms")

