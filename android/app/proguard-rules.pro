# Jatri release rules. Kotlinx Serialization, Hilt, Room and Navigation ship their own consumer rules.
# Keep @Serializable navigation routes' companion serializers (type-safe navigation looks them up).
-keepclassmembers @kotlinx.serialization.Serializable class com.jatri.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
