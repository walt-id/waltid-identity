# Android's TLS provider requests BKS after X509CertificateUtil installs Bouncy Castle.
# Bouncy Castle looks up the mapping and the keystore implementation by class name.
-keep class org.bouncycastle.jcajce.provider.keystore.BC$Mappings { *; }
-keep class org.bouncycastle.jcajce.provider.keystore.bc.BcKeyStoreSpi** { *; }
