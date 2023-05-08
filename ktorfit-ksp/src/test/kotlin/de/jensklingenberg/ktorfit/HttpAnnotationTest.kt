package de.jensklingenberg.ktorfit

import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import de.jensklingenberg.ktorfit.model.KtorfitError
import org.junit.Assert
import org.junit.Test
import java.io.File

class HttpAnnotationTest() {


    @Test
    fun testFunctionWithGET() {
        val expectedSource = """// Generated by Ktorfit
package com.example.api

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.`internal`.KtorfitService
import de.jensklingenberg.ktorfit.`internal`.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import kotlin.OptIn

@OptIn(InternalKtorfitApi::class)
public class _TestServiceImpl : TestService, KtorfitService {
  public override lateinit var ktorfitClient: Client

  public override suspend fun test(): String {
    val _ext: HttpRequestBuilder.() -> Unit = {
        this.method = HttpMethod.parse("GET") 
        }
    val requestData = RequestData(relativeUrl="user",
        returnTypeData = TypeData("kotlin.String"),
        requestTypeInfo=typeInfo<String>(),
        returnTypeInfo = typeInfo<String>(),
        ktorfitRequestBuilder = _ext) 

    return ktorfitClient.suspendRequest<String, String>(requestData)!!
  }
}

public fun Ktorfit.createTestService(): TestService = this.create(_TestServiceImpl())
"""

        val source = SourceFile.kotlin(
            "Source.kt", """
      package com.example.api
import de.jensklingenberg.ktorfit.http.GET

interface TestService {

    @GET("user")
    suspend fun test(): String
    
}
    """
        )

        val compilation = getCompilation(listOf(source))
        val result = compilation.compile()
        assertThat(result.exitCode).isEqualTo(ExitCode.OK)

        val generatedSourcesDir = compilation.kspSourcesDir
        val generatedFile = File(
            generatedSourcesDir,
            "/kotlin/com/example/api/_TestServiceImpl.kt"
        )
        assertThat(generatedFile.exists()).isTrue()
        Assert.assertEquals(expectedSource, generatedFile.readText())
    }

    @Test
    fun testCustomHttpMethod() {

        val source = SourceFile.kotlin(
            "Source.kt", """     
package com.example.api
import de.jensklingenberg.ktorfit.http.HTTP
import de.jensklingenberg.ktorfit.http.Body

interface TestService {

    @HTTP("CUSTOM","user")
    suspend fun test(): String
    
}"""
        )


        val expectedFunctionText = """ public override suspend fun test(): String {
    val _ext: HttpRequestBuilder.() -> Unit = {
        this.method = HttpMethod.parse("CUSTOM") 
        }
    val requestData = RequestData(relativeUrl="user",
        returnTypeData = TypeData("kotlin.String"),
        requestTypeInfo=typeInfo<String>(),
        returnTypeInfo = typeInfo<String>(),
        ktorfitRequestBuilder = _ext) 

    return ktorfitClient.suspendRequest<String, String>(requestData)!!
  }"""

        val compilation = getCompilation(listOf(source))
        val result = compilation.compile()
        Truth.assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

        val generatedSourcesDir = compilation.kspSourcesDir
        val generatedFile = File(
            generatedSourcesDir,
            "/kotlin/com/example/api/_TestServiceImpl.kt"
        )
        Truth.assertThat(generatedFile.exists()).isTrue()
        assertThat(generatedFile.readText().contains(expectedFunctionText)).isTrue()
    }

    @Test
    fun testCustomHttpMethodWithBody() {

        val source = SourceFile.kotlin(
            "Source.kt", """     
package com.example.api
import de.jensklingenberg.ktorfit.http.HTTP
import de.jensklingenberg.ktorfit.http.Body

interface TestService {

    @HTTP("GET2","user",true)
    suspend fun test(@Body body: String): String
    
}"""
        )


        val expectedFunctionText = """public override suspend fun test(body: String): String {
    val _ext: HttpRequestBuilder.() -> Unit = {
        this.method = HttpMethod.parse("GET2")
        setBody(body) 
        }
    val requestData = RequestData(relativeUrl="user",
        returnTypeData = TypeData("kotlin.String"),
        requestTypeInfo=typeInfo<String>(),
        returnTypeInfo = typeInfo<String>(),
        ktorfitRequestBuilder = _ext) 

    return ktorfitClient.suspendRequest<String, String>(requestData)!!
  }"""

        val compilation = getCompilation(listOf(source))
        val result = compilation.compile()
        Truth.assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

        val generatedSourcesDir = compilation.kspSourcesDir
        val generatedFile = File(
            generatedSourcesDir,
            "/kotlin/com/example/api/_TestServiceImpl.kt"
        )
        Truth.assertThat(generatedFile.exists()).isTrue()
        assertThat(generatedFile.readText().contains(expectedFunctionText)).isTrue()
    }

    @Test
    fun whenMultipleHttpMethodsFound_throwError() {
        val source = SourceFile.kotlin(
            "Source.kt", """
      package com.example.api

import com.example.model.github.GithubFollowerResponseItem
import com.example.model.github.Issuedata
import com.example.model.github.TestReeeItem
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import kotlinx.coroutines.flow.Flow

interface GithubService {

    @GET("user/followers")
    @POST("repos/foso/experimental/issues")
    suspend fun test(): String
    
}
    """
        )

        val compilation = getCompilation(listOf(source))
        val result = compilation.compile()
        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        Assert.assertTrue(result.messages.contains(KtorfitError.ONLY_ONE_HTTP_METHOD_IS_ALLOWED))
    }
}