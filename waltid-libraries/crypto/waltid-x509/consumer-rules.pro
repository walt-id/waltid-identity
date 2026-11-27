# Bouncy Castle loads this mapping by name. Android TLS requests its BKS keystore.
-keep,allowoptimization class org.bouncycastle.jcajce.provider.keystore.BC$Mappings { public <init>(); }
# JCA constructs the keystores registered by that mapping by name and public no-arg constructor.
-keepclasseswithmembers,allowoptimization class org.bouncycastle.jcajce.provider.keystore.bc.BcKeyStoreSpi$* { public <init>(); }
