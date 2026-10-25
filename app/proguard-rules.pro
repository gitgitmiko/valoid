-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Credential Manager memuat penyedia Play Services lewat refleksi.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
