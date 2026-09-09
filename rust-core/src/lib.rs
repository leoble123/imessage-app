uniffi::setup_scaffolding!();

/// Fetches this relay's reported device identity (calls our own relay server's
/// /api/v1/bridge/get-version-info) and returns it as a JSON string.
///
/// This is the first proof-of-concept call across the Kotlin<->Rust bridge:
/// it exercises the same rustpush RelayConfig code path that OpenBubbles'
/// Flutter app uses, from a plain Kotlin/Compose app instead. If this
/// succeeds end to end (Kotlin calls it, gets real JSON back), the hardest
/// unknown - can this Rust core be driven from a native Android app at all -
/// is proven.
#[uniffi::export(async_runtime = "tokio")]
pub async fn fetch_relay_versions(host: String, code: String) -> Result<String, String> {
    let versions = rustpush::RelayConfig::get_versions(&host, &code, &None)
        .await
        .map_err(|e| e.to_string())?;
    serde_json::to_string(&versions).map_err(|e| e.to_string())
}
