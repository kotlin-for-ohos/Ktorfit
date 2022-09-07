package de.jensklingenberg.ktorfit.demo


import com.example.api.JsonPlaceHolderApi
import com.example.model.MyOwnResponse
import com.example.model.MyOwnResponseConverter
import com.example.model.StringToIntRequestConverter
import de.jensklingenberg.ktorfit.converter.builtin.CallResponseConverter
import de.jensklingenberg.ktorfit.converter.builtin.FlowResponseConverter
import de.jensklingenberg.ktorfit.ktorfit
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json


val jvmClient = HttpClient {

    install(Logging) {
        level = LogLevel.ALL
    }

    install(ContentNegotiation) {
        json(Json { isLenient = true; ignoreUnknownKeys = true })
    }
    install(WebSockets)
    this.developmentMode = true
    expectSuccess = false


}


val jvmKtorfit = ktorfit {
    baseUrl(JsonPlaceHolderApi.baseUrl)
    httpClient(jvmClient)
    responseConverter(
        FlowResponseConverter(),
        RxRequestConverter(),
        CallResponseConverter(),
        MyOwnResponseConverter()
    )
    requestConverter(
        StringToIntRequestConverter()
    )
}


fun main() {


    runBlocking {
       KtorfitClient(jvmKtorfit).socket()

        val response = exampleApi.getPersonById2(2)

        val test = api.getCommentsByPostIdResponse("3")

        when (test) {
            is MyOwnResponse.Success -> {
                test
            }

            else -> {
                test
            }
        }


        delay(3000)
    }

}
