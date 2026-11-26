import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            namespace = jatriNamespace
            compileSdk = JatriBuild.COMPILE_SDK
            compileSdkMinor = JatriBuild.COMPILE_SDK_MINOR
            defaultConfig {
                minSdk = JatriBuild.MIN_SDK
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JatriBuild.JAVA_VERSION
                targetCompatibility = JatriBuild.JAVA_VERSION
                // java.time (LocalDate etc.) on API 24-25.
                isCoreLibraryDesugaringEnabled = true
            }
            testOptions.unitTests.isIncludeAndroidResources = true
        }
        configureKotlin()
        dependencies { add("coreLibraryDesugaring", libs.lib("desugar-jdk-libs")) }
        dependencies {
            add("testImplementation", libs.lib("junit"))
        }
    }
}
