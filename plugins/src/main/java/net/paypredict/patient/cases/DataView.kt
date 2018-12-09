package net.paypredict.patient.cases

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/15/2018.
 */
@Target(AnnotationTarget.PROPERTY)
annotation class DataView(
    val label: String,
    val description: String = "",
    val section: String = "",
    val isVisible: Boolean = true,
    val order: Int = -1,
    val docKey: String = "",
    val srtKey: String = "",
    val projectionKeys: Array<String> = [],
    val filterKeys: Array<String> = [],
    val sortable: Boolean = true,
    val flexGrow: Int = 1
)

inline fun DataView.ifHasDocKey(action: (docKey: String) -> Unit) {
    if (docKey.isNotEmpty()) action(docKey)
}

inline fun DataView.ifHasProjectionKeys(action: (filterKeys: Array<String>) -> Unit) {
    if (projectionKeys.isNotEmpty()) action(projectionKeys)
}

inline fun DataView.ifHasFilterKeys(action: (filterKeys: Array<String>) -> Unit) {
    if (filterKeys.isNotEmpty()) action(filterKeys)
}

inline fun DataView.ifSortable(action: (sortKey: String) -> Unit) {
    if (sortable) {
        var sortKey = srtKey
        if (sortKey.isEmpty() && docKey.isNotEmpty()) sortKey = docKey
        if (sortKey.isNotEmpty())
            action(sortKey)
    }
}