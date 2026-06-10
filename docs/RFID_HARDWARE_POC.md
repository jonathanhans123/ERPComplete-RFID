# RFD90 Hardware POC Checklist

Use this checklist on a **physical Android phone** (Android 10+) paired with the Zebra RFD90 over Bluetooth.

## Prerequisites

- RFD90 charged; Bluetooth adaptor attached if required
- UHF passive tags (EPC Gen2)
- [123RFID Mobile](vendor/reference/123RFID_Mobile_2.0.5.275/123_RFID_Mobile-2.0.5.275.apk) installed for baseline validation
- ERPComplete RFID app (`com.erpcomplete.rfid`) for integrated testing

## Pairing SOP (generic Android)

1. Power on RFD90; ensure Bluetooth is enabled on the phone.
2. Open **123RFID Mobile** → **Connect** → select Bluetooth transport.
3. Use **Scan-To-Connect** (RFD90 trigger) or NFC tap-to-pair if available.
4. Confirm reader appears in the paired list; note the reader name (e.g. `RFD90-xxxxx`).
5. Run a short inventory (pull trigger); confirm tags appear in the UI.

## POC exit criteria

| Test | Pass? | Notes |
|------|-------|-------|
| Pair RFD90 in under 2 minutes | | |
| 30-minute inventory session without disconnect | | |
| Write EPC to blank tag (Encode screen: request → scan blank → write on device → verify → confirm) | | |
| Locate known tagged item (signal 0–100) | | |
| EPC prefix pre-filter in dense areas (Settings) | | |
| Range vs dense RF profile (Settings) | | |
| Reader diagnostics in Settings (firmware, battery) | | |
| ERPComplete RFID app discovers reader | | |
| ERPComplete RFID app syncs reads to Laravel API | | |

## Tag environment

- Record tag type (on-metal, standard, size)
- Note read range at receiving desk vs rack aisle
- Flag metal/liquid interference locations

## Troubleshooting

- **Reader not listed:** Re-pair from phone Bluetooth settings; restart RFD90.
- **Drops connection:** Disable battery optimization for the RFID app; avoid consumer phones with aggressive BT power saving.
- **No tags read:** Check region/power settings; verify tag is UHF Gen2.
