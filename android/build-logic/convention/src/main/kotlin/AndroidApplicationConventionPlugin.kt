import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            compileSdk = JatriBuild.COMPILE_SDK
            compileSdkMinor = JatriBuild.COMPILE_SDK_MINOR
            defaultConfig {
                minSdk = JatriBuild.MIN_SDK
                targetSdk = JatriBuild.TARGET_SDK
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                vectorDrawables.useSupportLibrary = true
            }
            compileOptions {
                sourceCompatibility = JatriBuild.JAVA_VERSION
                targetCompatibility = JatriBuild.JAVA_VERSION
                // java.time (LocalDate etc.) on API 24-25.
                isCoreLibraryDesugaringEnabled = true
            }
            testOptions.unitTests.isIncludeAndroidResources = true
            lint {
                // Lint the whole app (all modules) from :app, and treat missing translations as errors.
                checkDependencies = true
                error += "MissingTranslation"
                abortOnError = true
            }
        }
        configureKotlin()
        dependencies { add("coreLibraryDesugaring", libs.lib("desugar-jdk-libs")) }
    }
}
