package net.paypredict.patient.cases.view

import com.mongodb.client.MongoCollection
import com.vaadin.flow.component.Composite
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.Label
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.provider.DataProvider
import com.vaadin.flow.data.provider.Query
import com.vaadin.flow.data.provider.QuerySortOrder
import com.vaadin.flow.data.provider.SortDirection
import com.vaadin.flow.data.value.ValueChangeMode
import net.paypredict.patient.cases.DataView
import net.paypredict.patient.cases.MetaData
import net.paypredict.patient.cases.VaadinBean
import net.paypredict.patient.cases.metaDataMap
import net.paypredict.patient.cases.mongo.*
import org.bson.Document
import org.bson.conversions.Bson

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 9/11/2018.
 */
class PokitDokPayerGrid : Composite<Grid<TradingPartnerItem>>() {
    override fun initContent(): Grid<TradingPartnerItem> =
        Grid(TradingPartnerItem::class.java)

    var width: String?
        get() = content.width
        set(value) {
            content.width = value
        }

    var height: String?
        get() = content.height
        set(value) {
            content.height = value
        }

    var value: TradingPartnerItem? = null
        get() = content.selectedItems.firstOrNull()
        set(value) {
            selectById(value?._id)
            field = value
        }

    fun selectById(payerId: String?) {
        for ((index, document) in collection().find().projection(doc {}).withIndex()) {
            if (document["_id"] == payerId) {
                content.select(document.toTradingPartnerItem())
                content.scrollTo(index)
                break
            }
        }
        content.focus()
    }

    private var filter: Document = doc { }

    init {
        content.setColumns(
            *TradingPartnerItem.META_DATA_MAP
                .entries
                .asSequence()
                .sortedBy { it.value.view.order }
                .map { it.key }
                .toList()
                .toTypedArray())
        content.getColumnByKey("name").apply {
            flexGrow = 5
            setHeader(HorizontalLayout().apply {
                isPadding = false
                defaultVerticalComponentAlignment = FlexComponent.Alignment.BASELINE
                this += Label("Name")
                this += TextField().apply {
                    placeholder = "Find..."
                    valueChangeMode = ValueChangeMode.EAGER
                    width = "32em"
                    addValueChangeListener {
                        filter = if (value.isNullOrBlank()) doc { } else doc {
                            doc[`$text`] = doc { doc[`$search`] = value }
                        }
                        content.scrollTo(0)
                        updateDataProvider()
                    }
                }
            })
        }

        updateDataProvider()
    }

    private fun updateDataProvider() {
        content.dataProvider = DataProvider.fromFilteringCallbacks(
            { query: Query<TradingPartnerItem, Unit> ->
                collection()
                    .find(filter)
                    .projection(projection(filter))
                    .sort(query.toMongoSort(filter))
                    .skip(query.offset)
                    .limit(query.limit)
                    .map { it.toTradingPartnerItem() }
                    .toList()
                    .stream()
            },
            { collection().find(filter).count() }
        )
    }

    companion object {
        private val projectionName: Document by lazy {
            doc {
                doc["data.name"] = 1
                doc["data.payer_id"] = 1
            }
        }
        private val projectionScore: Document by lazy {
            doc {
                doc["data.name"] = 1
                doc["data.payer_id"] = 1
                doc["score"] = doc { doc[`$meta`] = "textScore" }
            }
        }

        private fun projection(filter: Document): Document =
            if (filter.isEmpty()) projectionName else projectionScore

        private fun collection(): MongoCollection<Document> =
            DBS.Collections.tradingPartners().apply {
                createIndex(doc {
                    doc["data.name"] = "text"
//                    doc["data.payer_id"] = "text"
                })
            }

        private fun Document.toTradingPartnerItem(): TradingPartnerItem =
            TradingPartnerItem(
                _id = opt("_id"),
                name = opt("data", "name"),
                payerId = opt("data", "payer_id")
            )

        private fun Query<TradingPartnerItem, *>.toMongoSort(filter: Document): Bson? {
            if (sortOrders.isEmpty()) {
                if (filter.isEmpty()) return null
                return doc {
                    doc["score"] = doc {
                        doc[`$meta`] = "textScore"
                    }
                }
            }
            return Document().also { document ->
                sortOrders.forEach { sortOrder: QuerySortOrder ->
                    when (sortOrder.sorted) {
                        TradingPartnerItem::_id.name -> "_id"
                        TradingPartnerItem::name.name -> "data.name"
                        TradingPartnerItem::payerId.name -> "data.payer_id"
                        else -> null
                    }?.let {
                        document[it] = when (sortOrder.direction) {
                            null,
                            SortDirection.ASCENDING -> 1
                            SortDirection.DESCENDING -> -1
                        }
                    }
                }
            }
        }

    }
}

@VaadinBean
data class TradingPartnerItem(
    @DataView("_id", isVisible = false)
    val _id: String?,

    @DataView("Name", order = 10)
    val name: String?,

    @DataView("Payer ID", order = 20)
    val payerId: String?
) {
    companion object {
        val META_DATA_MAP: Map<String, MetaData<TradingPartnerItem>> by lazy { metaDataMap<TradingPartnerItem>() }
    }
}
