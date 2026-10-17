# AGP 9.4's proguard-android-optimize.txt without -allowaccessmodification.
#
# With access modification allowed, R8 sets ACC_PUBLIC on the InnerClasses entry of
# non-public nested classes (dalvik.annotation.InnerClass accessFlags) while leaving
# the dex class flags package-private, even for -keep'd classes. ART reports
# Class.getModifiers() from that annotation, so Guice's DefaultConstructionProxyFactory
# sees a "public" class, skips setAccessible(true), and the constructor call fails with
# IllegalAccessException (117 HD: ShadowMapOverlay$Shader,
# ModelStreamingManager$StreamingContext, ...). The optimizer is off anyway
# (-dontoptimize in proguard-rules.pro), so nothing is lost.

-keepattributes AnnotationDefault,
                EnclosingMethod,
                InnerClasses,
                RuntimeVisibleAnnotations,
                RuntimeVisibleParameterAnnotations,
                RuntimeVisibleTypeAnnotations,
                Signature

-keep public class com.google.vending.licensing.ILicensingService
-keep public class com.android.vending.licensing.ILicensingService
-keep public class com.google.android.vending.licensing.ILicensingService
-dontnote com.android.vending.licensing.ILicensingService
-dontnote com.google.vending.licensing.ILicensingService
-dontnote com.google.android.vending.licensing.ILicensingService

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepclassmembers public class * extends android.view.View {
    void set*(***);
    *** get*();
}

-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-dontnote android.support.**
-dontnote androidx.**
-dontwarn android.support.**
-dontwarn androidx.**

-keep class android.support.annotation.Keep
-keep @android.support.annotation.Keep class * {*;}
-keepclasseswithmembers class * {
    @android.support.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @android.support.annotation.Keep <fields>;
}
-keepclasseswithmembers class * {
    @android.support.annotation.Keep <init>(...);
}

-dontnote org.apache.http.**
-dontnote android.net.http.**
-dontnote java.lang.invoke.**
