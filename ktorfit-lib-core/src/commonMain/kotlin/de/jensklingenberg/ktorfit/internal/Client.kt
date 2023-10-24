package de.jensklingenberg.ktorfit.internal

import io.ktor.client.statement.HttpStatement
import kotlin.reflect.KClass

@InternalKtorfitApi
public interface Client {

    /**
     * This will handle all requests for functions without suspend modifier
     */
    public fun <T> request(
        returnTypeData: TypeData,
        statement: suspend () -> HttpStatement
    ): T?

    /**
     * This will handle all requests for functions with suspend modifier
     * Used by generated Code
     */
    public suspend fun <T> suspendRequest(
        typeData: TypeData,
        statement: suspend () -> HttpStatement
    ): T?

    /**
     * Convert [data] of type [parameterType] to [requestType]
     * @return converted [data]
     */
    public fun <T : Any> convertParameterType(data: Any, parameterType: KClass<*>, requestType: KClass<T>): T

}