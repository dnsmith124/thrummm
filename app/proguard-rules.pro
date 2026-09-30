# Nothing needs keeping: the app uses no reflection or serialization, and the activity and
# services are kept through the manifest. Saved enum settings rely on Enum.name, which R8
# preserves.

# Release builds don't log: the listener's debug line names the notifying app.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
