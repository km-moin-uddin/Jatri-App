import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Pure Kotlin modules (no Android), e.g. `:core:model`. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-jvm"))
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JatriBuild.JAVA_VERSION
            targetCompatibility = JatriBuild.JAVA_VERSION
        }
        configureKotlin()
        dependencies {
            add("testImplementation", libs.lib("junit"))
        }
    }
}
