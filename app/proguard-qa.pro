# QA keeps SDK diagnostics while retaining its other consumer rules.

# gRPC related - https://github.com/grpc/grpc-java/issues/6612
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Prevent OKHttp from causing warnings for consumers of the SDK
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
