-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations
-keepattributes AnnotationDefault

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

-keep class com.google.ai.edge.gallery.api.** { *; }
-keep class com.google.ai.edge.gallery.ui.** { *; }
-keep class com.google.ai.edge.gallery.di.** { *; }

-keep class nanohttpd.** { *; }
-keep class fi.iki.elonen.** { *; }
-keep class org.nanohttpd.** { *; }

-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
  <fields>;
}

-keepclassmembers class com.google.ai.edge.gallery.api.model.** {
    <fields>;
}

-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-dontwarn kotlinx.coroutines.**
-dontwarn com.google.ai.edge.gallery.**
