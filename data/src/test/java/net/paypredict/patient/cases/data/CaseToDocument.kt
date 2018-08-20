package net.paypredict.patient.cases.data

/**
 * Created by alexei.vylegzhanin@gmail.com on 8/14/2018.
 */
fun main(args: Array<String>) {
    val case1 = Case(
        req = "123",
        ptnLast = "Test",
        dxV = listOf("Q1")
    )
    val document1 = case1.toDocument()
    println(document1.toJson())

    val case2 = document1.toCase()
    val document2 = case2.toDocument()
    println(document2.toJson())
}