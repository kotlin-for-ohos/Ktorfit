package de.jensklingenberg.ktorfit.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension

class KtorfitGradlePlugin : Plugin<Project> {
    companion object {
        const val GRADLE_TASKNAME = "ktorfit"
    }

    override fun apply(project: Project) {

        with(project) {
            extensions.create(GRADLE_TASKNAME, KtorfitGradleConfiguration::class.java)

        }
        project.pluginManager.apply(KtorfitCompilerSubPlugin::class.java)
    }


}