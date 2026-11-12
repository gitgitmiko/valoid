-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Credential Manager memuat penyedia Play Services lewat refleksi.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# Kredensial Google dibaca dari Bundle lewat nama kelas; jangan sampai R8 menghapus atau mengganti namanya.
-keep class com.google.android.libraries.identity.googleid.** { *; }
