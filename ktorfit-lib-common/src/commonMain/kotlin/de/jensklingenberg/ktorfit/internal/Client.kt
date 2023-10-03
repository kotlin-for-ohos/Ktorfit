package de.jensklingenberg.ktorfit.internal

import kotlin.reflect.KClass

@OptIn(InternalKtorfitApi::class)
public interface Client {

    /**
     * This will handle all requests for functions without suspend modifier
     */
    public fun <T> request(requestData: RequestData): T?

    /**
     * This will handle all requests for functions with suspend modifier
     * Used by generated Code
     */
    public suspend fun <T> suspendRequest(requestData: RequestData): T?

    /**
     * Convert [data] of type [parameterType] to [requestType]
     * @return converted [data]
     */
    public fun <T : Any> convertParameterType(data: Any, parameterType: KClass<*>, requestType: KClass<T>): T

    /**
     * Set baseUrl of the client
     */
    public val baseUrl: String
}