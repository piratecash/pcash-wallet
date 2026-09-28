# QA keeps SDK diagnostics while retaining its other consumer rules.

# https://github.com/grpc/grpc-java/blob/master/android/proguard-rules.txt
-keepclassmembers class io.grpc.okhttp.OkHttpChannelBuilder {
  io.grpc.okhttp.OkHttpChannelBuilder forTarget(java.lang.String);
  io.grpc.okhttp.OkHttpChannelBuilder scheduledExecutorService(java.util.concurrent.ScheduledExecutorService);
  io.grpc.okhttp.OkHttpChannelBuilder sslSocketFactory(javax.net.ssl.SSLSocketFactory);
  io.grpc.okhttp.OkHttpChannelBuilder transportExecutor(java.util.concurrent.Executor);
}

# https://github.com/grpc/grpc-java/issues/9611
-keepclassmembers, allowoptimization class io.grpc.okhttp.OkHttpChannelProvider {
    <init>();
}

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
