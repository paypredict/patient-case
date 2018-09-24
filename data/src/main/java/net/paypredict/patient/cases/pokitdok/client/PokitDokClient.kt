package net.paypredict.patient.cases.pokitdok.client

import net.paypredict.patient.cases.toDigest
import net.paypredict.patient.cases.toHexString
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import javax.json.*
import kotlin.concurrent.withLock

/**
 * <p>
 * Created by alexei.vylegzhanin@gmail.com on 8/24/2018.
 */

data class EligibilityQuery(
    val member: Member,
    val provider: Provider = Conf.defaultProvider,
    val trading_partner_id: String
) {
    data class Member(
        val id: String,
        val first_name: String,
        val last_name: String,
        val birth_date: String,
        val gender: String?
    ) {
        companion object {
            val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        }
    }

    data class Provider(
        val organization_name: String,
        val npi: String
    )
}

fun <T> EligibilityQuery.query(result: (InputStreamReader) -> T): T =
    authQuery(
        path = "api/v4/eligibility/",
        setup = { outputStream.write(toJson().toString().toByteArray()) },
        result = result
    )

fun EligibilityQuery.digest(): String =
    toJson().toDigest().toHexString()

fun <T> queryTradingPartners(result: (InputStreamReader) -> T): T =
    authQuery(
        method = "GET",
        path = "api/v4/tradingpartners/",
        contentType = null,
        result = result
    )

class PokitDokApiException(
    val responseCode: Int,
    val responseMessage: String?,
    val responseJson: JsonObject
) : IOException("API error: $responseCode - $responseMessage")

private fun EligibilityQuery.toJson(): JsonObject =
    Json.createObjectBuilder()
        .add("member", member.toJson())
        .add("provider", provider.toJson())
        .add("trading_partner_id", trading_partner_id)
        .build()

private fun EligibilityQuery.Member.toJson(): JsonObject =
    Json.createObjectBuilder()
        .also { builder ->
            builder.add("id", id)
            builder.add("first_name", first_name)
            builder.add("last_name", last_name)
            builder.add("birth_date", birth_date)
            gender?.let { builder.add("gender", it) }
        }
        .build()

private fun EligibilityQuery.Provider.toJson(): JsonObject =
    Json.createObjectBuilder()
        .add("organization_name", organization_name)
        .add("npi", npi)
        .build()

private val defaultCheckResponse: HttpURLConnection.() -> Unit =
    {
        if (responseCode != 200) {
            try {
                errorStream?.reader()?.readText()?.let { json ->
                    System.err.println(json)
                    throw PokitDokApiException(
                        responseCode = responseCode,
                        responseMessage = responseMessage,
                        responseJson = Json.createReader(json.reader()).readObject()
                    )
                }
            } catch (x: Throwable) {
                if (x is PokitDokApiException) throw x
            }
            throw PokitDokApiException(
                responseCode,
                responseMessage,
                Json.createObjectBuilder().build()
            )
        }
    }

private fun <T> http(
    method: String = "GET",
    path: String,
    checkResponse: HttpURLConnection.() -> Unit = defaultCheckResponse,
    setup: HttpURLConnection.() -> Unit = {},
    result: (InputStreamReader) -> T
): T {
    val connection: HttpURLConnection = URL(Conf.host + path).openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.setup()
    connection.checkResponse()
    return result(connection.inputStream.reader())
}

private fun <T> authQuery(
    method: String = "POST",
    path: String,
    contentType: String? = "application/json",
    doOutput: Boolean? = method == "POST",
    setup: HttpURLConnection.() -> Unit = {},
    result: (InputStreamReader) -> T
): T {
    val setup1: HttpURLConnection.() -> Unit = {
        setRequestProperty("Authorization", "Bearer " + Authorization.accessToken)
        contentType?.let { setRequestProperty("Content-Type", it) }
        doOutput?.let { this.doOutput = it }
        setup()
    }

    return try {
        http(
            method = method,
            path = path,
            checkResponse = {
                when (responseCode) {
                    401 -> throw UnauthorizedException()
                    200 -> {
                    }
                    else -> defaultCheckResponse()
                }
            },
            setup = setup1,
            result = result
        )
    } catch (e: UnauthorizedException) {
        Authorization.reset()
        http(
            method = method,
            path = path,
            setup = setup1,
            result = result
        )
    }
}

private class UnauthorizedException : IOException()

private object Authorization {
    private val lock = ReentrantLock()
    private var accessTokenF: String? = null

    val accessToken: String
        get() = lock.withLock {
            if (accessTokenF == null) {
                accessTokenF = oauth2token()
            }
            accessTokenF!!
        }

    fun reset() = lock.withLock {
        accessTokenF = null
    }

    private fun oauth2token(): String =
        http(
            method = "POST",
            path = "oauth2/token",
            setup = {
                setRequestProperty(
                    "Authorization",
                    "Basic $basic"
                )
                doOutput = true
                outputStream.write("grant_type=client_credentials".toByteArray())
            },
            result = {
                Json.createReader(it).readObject().getString("access_token")
            }
        )

    private val basic: String
        get() = Base64
            .getEncoder()
            .encodeToString((Conf.clientId + ":" + Conf.clientSecret).toByteArray())
}

private object Conf {
    val host: String
    val clientId: String
    val clientSecret: String
    val defaultProvider: EligibilityQuery.Provider by lazy {
        val provider: JsonObject = conf.getJsonObject("provider")
        EligibilityQuery.Provider(
            provider.getString("organization_name"),
            provider.getString("npi")
        )
    }

    private val conf: JsonObject =
        File("/PayPredict/conf/PokitDok.json")
            .absoluteFile
            .reader()
            .use { Json.createReader(it).readObject() }


    init {
        host = conf.getString("host", "https://platform.pokitdok.com/")
        clientId = conf.getString("clientId")
        clientSecret = conf.getString("clientSecret")
    }
}