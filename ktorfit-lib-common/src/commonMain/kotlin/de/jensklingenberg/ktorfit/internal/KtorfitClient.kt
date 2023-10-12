package de.jensklingenberg.ktorfit.internal

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.converter.KtorfitResult
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.reflect.*
import kotlin.reflect.KClass
import kotlin.reflect.cast

@OptIn(InternalKtorfitApi::class)
internal class KtorfitClient(private val ktorfit: Ktorfit) : Client {

    private val httpClient: HttpClient = ktorfit.httpClient
    override val baseUrl: String = ktorfit.baseUrl

    /**
     * This will handle all requests for functions without suspend modifier
     */
    override fun <ReturnType> request(
        requestData: RequestData,
        ktorfitRequestBuilder: HttpRequestBuilder.() -> Unit
    ): ReturnType? {
        val returnTypeData = requestData.getTypeData()

        ktorfit.nextResponseConverter(null, returnTypeData)?.convert {
            suspendRequest<HttpResponse>(
                RequestData(
                    ktorfitRequestBuilder = requestData.ktorfitRequestBuilder,
                    returnTypeName = "io.ktor.client.statement.HttpResponse",
                    returnTypeInfo = typeInfo<HttpResponse>()
                ), ktorfitRequestBuilder
            )!!
        } as ReturnType?

        val typeIsNullable = returnTypeData.typeInfo.kotlinType?.isMarkedNullable ?: false
        return if (typeIsNullable) {
            null
        } else {
            throw IllegalStateException("Add a ResponseConverter for " + returnTypeData.qualifiedName + " or make function suspend")
        }

    }

    /**
     * This will handle all requests for functions with suspend modifier
     * Used by generated Code
     */
    override suspend fun <ReturnType> suspendRequest(
        requestData: RequestData,
        ktorfitRequestBuilder: HttpRequestBuilder.() -> Unit
    ): ReturnType? {
        val returnTypeData = requestData.getTypeData()

        try {
            if (returnTypeData.typeInfo.type == HttpStatement::class) {
                return httpClient.prepareRequest {
                    requestBuilder(requestData)
                } as ReturnType
            }

            ktorfit.nextSuspendResponseConverter(null, returnTypeData)?.let {
                val result: KtorfitResult = try {
                    KtorfitResult.Success(httpClient.request {
                        requestBuilder(requestData)
                    })
                } catch (exception: Exception) {
                    KtorfitResult.Failure(exception)
                }
                return it.convert(result) as ReturnType?
            }
            throw IllegalStateException("No suspend converter found")
        } catch (exception: Exception) {
            val typeIsNullable = returnTypeData.typeInfo.kotlinType?.isMarkedNullable ?: false
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
            ?: throw IllegalStateException("No RequestConverter found to convert ${parameterType.simpleName} to ${requestType.simpleName}")

    }

    private fun HttpRequestBuilder.requestBuilder(
        requestData: RequestData
    ) {
        requestData.ktorfitRequestBuilder(this)
    }
}
