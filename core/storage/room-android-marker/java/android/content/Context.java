package android.content;

/**
 * Marker only: compileOnly for every JVM target that runs Room's KSP processor, never packaged.
 * Room rejects blocking DAO functions unless android.content.Context is visible to the processor.
 */
public abstract class Context {
}
