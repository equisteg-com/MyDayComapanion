# Security tests

Automated (run in CI): `domain/src/test/.../security/AgentSecurityTest.kt`, `policy/ToolAuthorizationPolicyTest.kt`,
`memory/MemoryPolicyTest.kt`, `ai/TaskExtractionValidatorTest.kt`, `app/src/test/.../ManifestSecurityTest.kt`, plus the
CI APK permission check and secret scan.

Manual before alpha: verify `db.key.v1.enc` is unreadable without the Keystore key; verify `adb backup` / device
transfer exclude app data; inspect the APK for network permissions with `aapt2 dump permissions`.
