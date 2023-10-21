package de.jensklingenberg.ktorfit.model

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import de.jensklingenberg.ktorfit.model.KtorfitError.Companion.PROPERTIES_NOT_SUPPORTED
import de.jensklingenberg.ktorfit.model.annotations.FormUrlEncoded
import de.jensklingenberg.ktorfit.model.annotations.Multipart
import de.jensklingenberg.ktorfit.model.annotations.ParameterAnnotation.*
import de.jensklingenberg.ktorfit.utils.addImports
import de.jensklingenberg.ktorfit.utils.getFileImports

/**
 * @param name of the interface that contains annotations
 * @param superClasses List of qualifiedNames of interface that a Ktorfit interface extends
 */
data class ClassData(
    val name: String,
    val packageName: String,
    val functions: List<FunctionData>,
    val imports: List<String>,
    val superClasses: List<KSTypeReference> = emptyList(),
    val properties: List<KSPropertyDeclaration> = emptyList(),
    val modifiers: List<KModifier> = emptyList(),
    val ksFile: KSFile
)

const val WILDCARDIMPORT = "WILDCARDIMPORT"

/**
 * Transform a [ClassData] to a [FileSpec] for KotlinPoet
 */
fun ClassData.getImplClassFileSource(resolver: Resolver): String {
    val classData = this
    val optinAnnotation = AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
        .addMember("InternalKtorfitApi::class")
        .build()

    val suppressAnnotation = AnnotationSpec.builder(ClassName("kotlin", "Suppress"))
        .addMember("\"warnings\"")
        .build()

    val createExtensionFunctionSpec = getCreateExtensionFunctionSpec(classData)

    val properties = classData.properties.map { property ->
        val propBuilder = PropertySpec.builder(
            property.simpleName.asString(),
            property.type.toTypeName()
        )
            .addModifiers(KModifier.OVERRIDE)
            .mutable(property.isMutable)
            .getter(
                FunSpec.getterBuilder()
                    .addStatement(PROPERTIES_NOT_SUPPORTED)
                    .build()
            )

        if (property.isMutable) {
            propBuilder.setter(
                FunSpec.setterBuilder()
                    .addParameter("value", property.type.toTypeName())
                    .build()
            )
        }

        propBuilder.build()
    }

    val implClassName = "_${classData.name}Impl"

    val clientProperty = PropertySpec
        .builder(
            clientClass.objectName,
            TypeVariableName(clientClass.name),
            listOf(KModifier.OVERRIDE, KModifier.LATEINIT)
        )
        .mutable(true)
        .build()


    val implClassSpec = TypeSpec.classBuilder(implClassName)
        .addAnnotation(
            optinAnnotation
        )

        .addModifiers(classData.modifiers)
        .addSuperinterface(ClassName(classData.packageName, classData.name))
        .addSuperinterface(ktorfitServiceClassName)
        .addKtorfitSuperInterface(classData.superClasses)
        .addFunctions(classData.functions.map { it.toFunSpec(resolver) })
        .addProperty(
            clientProperty
        )
        .addProperties(properties)
        .build()

    return FileSpec.builder(classData.packageName, implClassName)
        .addAnnotation(suppressAnnotation)
        .addFileComment("Generated by Ktorfit")
        .addImports(classData.imports)
        .addType(implClassSpec)
        .addFunction(createExtensionFunctionSpec)
        .build()
        .toString()
        .replace(WILDCARDIMPORT, "*")
}

/**
 * public fun Ktorfit.createExampleApi(): ExampleApi = this.create(_ExampleApiImpl()
 */
private fun getCreateExtensionFunctionSpec(
    classData: ClassData
): FunSpec {
    return FunSpec.builder("create${classData.name}")
        .addModifiers(classData.modifiers)
        .addStatement("return this.create(_${classData.name}Impl())")
        .receiver(TypeVariableName(ktorfitClass.name))
        .returns(TypeVariableName(classData.name))
        .build()
}


/**
 * Convert a [KSClassDeclaration] to [ClassData]
 * @param logger used to log errors
 * @return the transformed classdata
 */
fun KSClassDeclaration.toClassData(logger: KSPLogger): ClassData {
    val ksClassDeclaration = this
    val imports = ksClassDeclaration.getFileImports().toMutableList().apply {
        add("io.ktor.util.reflect.*")
        add("io.ktor.client.request.*")
        add("io.ktor.http.*")
        add(ktorfitClass.packageName + "." + ktorfitClass.name)
        add("de.jensklingenberg.ktorfit.internal.*")
    }

    val packageName = ksClassDeclaration.packageName.asString()
    val className = ksClassDeclaration.simpleName.asString()

    checkClassForErrors(this, logger)

    val functionDataList: List<FunctionData> =
        ksClassDeclaration.getDeclaredFunctions().toList().map { funcDeclaration ->
            return@map funcDeclaration.toFunctionData(logger)
        }

    if (functionDataList.any { it ->
            it.annotations.any { it is FormUrlEncoded || it is Multipart } ||
                    it.parameterDataList.any { param -> param.hasAnnotation<Field>() || param.hasAnnotation<Part>() }
        }) {
        imports.add("io.ktor.client.request.forms.*")
    }

    if (functionDataList.any { it.parameterDataList.any { param -> param.hasAnnotation<RequestType>() } }) {
        imports.add("kotlin.reflect.cast")
    }

    val filteredSupertypes =
        ksClassDeclaration.superTypes.toList().filterNot {
            /** In KSP Any is a supertype of an interface */
            it.toTypeName() == ANY
        }
    val properties = ksClassDeclaration.getAllProperties().toList()

    return ClassData(
        name = className,
        packageName = packageName,
        functions = functionDataList,
        imports = imports,
        superClasses = filteredSupertypes,
        properties = properties,
        modifiers = ksClassDeclaration.modifiers.mapNotNull { it.toKModifier() },
        ksFile = ksClassDeclaration.getKsFile()
    )
}

private fun checkClassForErrors(ksClassDeclaration: KSClassDeclaration, logger: KSPLogger) {
    val isJavaClass = ksClassDeclaration.origin.name == "JAVA"
    if (isJavaClass) {
        logger.error(KtorfitError.JAVA_INTERFACES_ARE_NOT_SUPPORTED, ksClassDeclaration)
        return
    }

    val isInterface = ksClassDeclaration.classKind == ClassKind.INTERFACE
    if (!isInterface) {
        logger.error(KtorfitError.API_DECLARATIONS_MUST_BE_INTERFACES, ksClassDeclaration)
        return
    }

    val hasTypeParameters = ksClassDeclaration.typeParameters.isNotEmpty()
    if (hasTypeParameters) {
        logger.error(
            KtorfitError.TYPE_PARAMETERS_ARE_UNSUPPORTED_ON + " ${ksClassDeclaration.simpleName.asString()}",
            ksClassDeclaration
        )
        return
    }

    if (ksClassDeclaration.packageName.asString().isEmpty()) {
        logger.error(KtorfitError.INTERFACE_NEEDS_TO_HAVE_A_PACKAGE, ksClassDeclaration)
        return
    }
}

private fun KSClassDeclaration.getKsFile(): KSFile {
    return this.containingFile ?: throw Error("Containing File for ${this.simpleName} was null")
}

/**
 * Support for extending multiple interfaces, is done with Kotlin delegation. Ktorfit interfaces can only extend other Ktorfit interfaces, so there will
 * be a generated implementation for each interface that we can use.
 */
fun TypeSpec.Builder.addKtorfitSuperInterface(superClasses: List<KSTypeReference>): TypeSpec.Builder {
    (superClasses).forEach { superClassReference ->
        val superClassDeclaration = superClassReference.resolve().declaration
        val superTypeClassName = superClassDeclaration.simpleName.asString()
        val superTypePackage = superClassDeclaration.packageName.asString()
        this.addSuperinterface(
            ClassName(superTypePackage, superTypeClassName),
            CodeBlock.of("%L._%LImpl()", superTypePackage, superTypeClassName)
        )
    }

    return this
}

