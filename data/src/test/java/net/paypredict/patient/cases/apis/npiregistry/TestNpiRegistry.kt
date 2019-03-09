package net.paypredict.patient.cases.apis.npiregistry

/**
 *
 *
 * Created by alexei.vylegzhanin@gmail.com on 2/27/2019.
 */
object TestNpiRegistry {
    @JvmStatic
    fun main(args: Array<String>) {
        val found = NpiRegistry.find(args[0])
        println(found)
    }
}