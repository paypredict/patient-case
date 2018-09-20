package net.paypredict.patient.cases.apis.smartystreets

object TestSmartystreetsApiKt {
    @JvmStatic
    fun main(args: Array<String>) {
        for (footNote in footNoteMap.values) {
            with(footNote) {
                println("$name\t$level\t\t$label\t\t$note")
            }
        }
    }
}