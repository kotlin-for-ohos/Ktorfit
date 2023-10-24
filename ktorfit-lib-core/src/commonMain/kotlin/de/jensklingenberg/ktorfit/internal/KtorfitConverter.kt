package de.jensklingenberg.ktorfit.internal

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.converter.KtorfitResult
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.util.reflect.typeInfo
import kotlin.reflect.KClass
import kotlin.reflect.cast

@InternalKtorfitApi
internal class KtorfitConverter(private val ktorfit: Ktorfit) : Client {


    /**
     * This will handle all requests for functions without suspend modifier
     */
    override fun <ReturnType> request(
        returnTypeData: TypeData,
        statement: suspend () -> HttpStatement
    ): ReturnType? {

        ktorfit.nextResponseConverter(null, returnTypeData)?.let { responseConverter ->
            return responseConverter.convert {
                suspendRequest(
                    TypeData.createTypeData(
                        "io.ktor.client.statement.HttpResponse",
                        typeInfo<HttpResponse>()
                    ),
                    statement
                )!!
            } as ReturnType?
        }

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
        typeData: TypeData,
        statement: suspend () -> HttpStatement
    ): ReturnType? {

        try {
            if (typeData.typeInfo.type == HttpStatement::class) {

            }

            ktorfit.nextSuspendResponseConverter(null, typeData)?.let { suspendResponseConverter ->
                val result: KtorfitResult = try {
                    KtorfitResult.Success(statement().execute())
                } catch (exception: Exception) {
                    KtorfitResult.Failure(exception)
                }
                return suspendResponseConverter.convert(result) as ReturnType?
            } ?: throw IllegalStateException("No SuspendResponseConverter found")


        } catch (exception: Exception) {
            val typeIsNullable = typeData.typeInfo.kotlinType?.isMarkedNullable ?: false
            return if (typeIsNullable) {
                null
            } else {
                throw exception
            }
        }
    }

    override fun <T : Any> convertParameterType(
        data: Any,
        parameterType: KClass<*>,
        requestType: KClass<T>
    ): T {
        ktorfit.nextRequestParameterConverter(null, parameterType, requestType)?.let {
            return requestType.cast(it.convert(data))
        }
            ?: throw IllegalStateException("No RequestConverter found to convert ${parameterType.simpleName} to ${requestType.simpleName}")

    }


}
