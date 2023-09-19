package de.jensklingenberg.ktorfit.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

open class KtorfitGradleConfiguration {
    /**
     * If the compiler plugin should be active
     */
    var enabled: Boolean = true

    /**
     * version number of Ktorfit
     */
    var version: String = "1.7.0" // remember to bump this version before any release!

    /**
     * used to get debug information from the compiler plugin
     */
    var logging: Boolean = false
}


class KtorfitGradleSubPlugin : KotlinCompilerPluginSupportPlugin {

    companion object {
        const val GROUP_NAME = "de.jensklingenberg.ktorfit"
        const val ARTIFACT_NAME = "compiler-plugin"
        const val COMPILER_PLUGIN_ID = "ktorfitPlugin"
        const val GRADLE_TASKNAME = "ktorfit"
    }

    private lateinit var ktorfitGradleConfiguration: KtorfitGradleConfiguration

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val gradleExtension = kotlinCompilation.target.project.getKtorfitConfig()

        return kotlinCompilation.target.project.provider {
            mutableListOf(
                SubpluginOption("enabled", gradleExtension.enabled.toString()),
                SubpluginOption("logging", gradleExtension.logging.toString())
            )
        }
    }

    private fun Project.getKtorfitConfig() =
        this.extensions.findByType(KtorfitGradleConfiguration::class.java) ?: KtorfitGradleConfiguration()

    private val Project.kotlinExtension: KotlinProjectExtension?
        get() = this.extensions.findByType<KotlinProjectExtension>()

    override fun apply(target: Project) {
        target.extensions.create(GRADLE_TASKNAME, KtorfitGradleConfiguration::class.java)
        ktorfitGradleConfiguration = target.getKtorfitConfig()
        val hasKspApplied = target.extensions.findByName("ksp") != null
        val version = ktorfitGradleConfiguration.version

        if (hasKspApplied) {
            val ktorfitKsp = "$GROUP_NAME:ktorfit-ksp:$version"

            when (val kotlinExtension = target.kotlinExtension) {
                is KotlinSingleTargetExtension<*> -> {
                    target.dependencies.add("ksp", ktorfitKsp)
                }

                is KotlinMultiplatformExtension -> {
                    target.afterEvaluate {
                        kotlinExtension.targets.forEach {

                            if (it.name == "metadata") return@forEach
                            val name = if (it.name == "commonMain") {
                                "ksp" + (it.name.capitalize()) + "MetaData"
                            } else {
                                "ksp" + (it.name.capitalize())
                            }
                            this.dependencies.add(name, ktorfitKsp)
                            this.dependencies.add(name + "Test", ktorfitKsp)
                        }
                    }
                }

                else -> { /* Do nothing */
                }
            }
        }
        super.apply(target)
    }

    override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getPluginArtifact(): SubpluginArtifact {
        return SubpluginArtifact(
            groupId = GROUP_NAME,
            artifactId = ARTIFACT_NAME,
            version = ktorfitGradleConfiguration.version
        )
    }
}


