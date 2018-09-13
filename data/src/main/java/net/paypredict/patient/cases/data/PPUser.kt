package net.paypredict.patient.cases.data

import org.bson.Document
import java.io.File

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/14/2018.
 */
data class PPUser(val userName: String)

fun PPUser.checkPassword(password: String): Boolean =
    Users().checkPassword(userName, password)

private class Users {
    private val conf: Document by lazy {
        val file = File("/PayPredict/conf/patient.cases.users.json")
        if (file.exists()) Document.parse(file.readText()) else Document()
    }

    fun checkPassword(userName: String, password: String): Boolean =
        conf.opt<String>("users", userName, "password") == password
}