# ERPComplete RFID (Android)

Proprietary Android companion app for **Zebra RFD90** UHF RFID integration with [ERPComplete](../ERPComplete) (Laravel).

## Stack

- Kotlin + Jetpack Compose
- Zebra RFID API3 (`rfidapi3lib-2.0.5.275.aar`)
- Min SDK 29 (Android 10+) for RFD90 Bluetooth

## Vendor assets

| Path | Contents |
|------|----------|
| `vendor/archives/` | Original Zebra zip downloads (SDK, 123RFID Mobile, 123RFID Desktop) |
| `vendor/sdk/` | Extracted SDK + HHSampleApp (gitignored — re-extract from archive) |
| `vendor/reference/` | 123RFID Mobile source reference (gitignored) |
| `vendor/desktop/` | 123RFID Desktop Windows installer (gitignored) |
| `app/libs/` | RFID `.aar` used at build time |

Re-extract after clone:

```powershell
Expand-Archive vendor/archives/Zebra_RFIDAPI3_SDK_2.0.5.275.zip -DestinationPath vendor/sdk -Force
Expand-Archive vendor/archives/123RFID_Mobile_2.0.5.275.zip -DestinationPath vendor/reference -Force
```

## Development

**Prerequisites:** `ANDROID_HOME` set, JDK 17+, emulator AVD `ERPComplete_RFID_API34` (or physical device).

```powershell
cd D:\ERPComplete-RFID
.\gradlew assembleDebug
.\gradlew installDebug
```

Configure ERP API URL in `app/build.gradle.kts` → `buildConfigField("API_BASE_URL", ...)`.

## Testing notes

- **Emulator:** UI, auth, API sync (no real Bluetooth RFID)
- **Physical phone + RFD90:** Pair over Bluetooth, then use Discover in app

## Related ERP repo

Laravel RFID APIs and data model live in `D:\ERPComplete` (separate repo).
