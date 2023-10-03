package de.jensklingenberg.ktorfit.internal

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.converter.builtin.DefaultSuspendResponseConverterFactory
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.reflect.*
import kotlin.reflect.KClass
import kotlin.reflect.cast

@OptIn(InternalKtorfitApi::class)
internal class KtorfitClient(private val ktorfit: Ktorfit) : Client {

    private val httpClient: HttpClient = ktorfit.httpClient
    override var baseUrl: String = ktorfit.baseUrl

    /**
     * This will handle all requests for functions without suspend modifier
     */
    override fun <ReturnType> request(
        requestData: RequestData
    ): ReturnType? {
        val returnTypeData = requestData.getTypeData()

        ktorfit.nextResponseConverter(null, returnTypeData)?.let { responseConverter ->

            return responseConverter.convert {
                try {
                    val data =
                        suspendRequest<HttpResponse>(
                            RequestData(
                                ktorfitRequestBuilder = requestData.ktorfitRequestBuilder,
                                returnTypeName = "io.ktor.client.statement.HttpResponse",
                                returnTypeInfo = typeInfo<HttpResponse>()
                            )
                        )
                    data!!
                } catch (ex: Exception) {
                    throw ex
                }
            } as ReturnType?
        }

        val typeIsNullable = returnTypeData.isNullable
        return if (typeIsNullable) {
            null
        } else {
            throw IllegalArgumentException("Add a ResponseConverter for " + returnTypeData.qualifiedName + " or make function suspend")
        }

    }

    /**
     * This will handle all requests for functions with suspend modifier
     * Used by generated Code
     */
    override suspend fun <ReturnType> suspendRequest(
        requestData: RequestData
    ): ReturnType? {
        val returnTypeData = requestData.getTypeData()

        try {
            if (returnTypeData.typeInfo.type == HttpStatement::class) {
                return httpClient.prepareRequest {
                    requestBuilder(requestData)
                } as ReturnType
            }

            ktorfit.nextSuspendResponseConverter(null, returnTypeData)?.let {

                val response = httpClient.request {
                    requestBuilder(requestData)
                }
                return it.convert(response) as ReturnType?
            }

            DefaultSuspendResponseConverterFactory().suspendResponseConverter(returnTypeData, ktorfit).let {
                val response = httpClient.request {
                    requestBuilder(requestData)
                }
                return it.convert(response) as ReturnType?
            }

        } catch (exception: Exception) {
            val typeIsNullable = returnTypeData.isNullable
            return if (typeIsNullable) {
                null
            } else {
                throw exception
            }
        }
    }


    override fun <T : Any> convertParameterType(data: Any, parameterType: KClass<*>, requestType: KClass<T>): T {
        ktorfit.nextRequestParameterConverter(null, parameterType, requestType)?.let {
            return requestType.cast(it.convert(data))
        }

        throw IllegalArgumentException("No RequestConverter found to convert ${parameterType.simpleName} to ${requestType.simpleName}")

    }

    private fun HttpRequestBuilder.requestBuilder(
        requestData: RequestData
    ) {
        requestData.ktorfitRequestBuilder(this)
    }


}
