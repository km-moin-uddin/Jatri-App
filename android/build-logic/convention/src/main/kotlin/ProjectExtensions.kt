import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Shared build constants for every Jatri module. */
object JatriBuild {
    const val COMPILE_SDK = 37
    const val COMPILE_SDK_MINOR = 1
    const val TARGET_SDK = 37
    const val MIN_SDK = 24
    val JAVA_VERSION = JavaVersion.VERSION_17
    val JVM_TARGET = JvmTarget.JVM_17
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun VersionCatalog.pluginId(alias: String) = findPlugin(alias).get().get().pluginId

/** Namespace derived from the Gradle path, e.g. `:feature:home` -> `com.jatri.feature.home`. */
internal val Project.jatriNamespace: String
    get() = "com.jatri" + path.replace(':', '.')

internal fun Project.configureKotlin() {
    // Not every module has unit tests yet; Gradle 9 would otherwise fail on an empty test task.
    tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JatriBuild.JVM_TARGET)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xannotation-default-target=param-property",
            )
        }
    }
}
