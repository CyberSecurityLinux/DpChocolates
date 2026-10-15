# DP Cocoa FG Rack Management — Android Native App (Prototype v0.1)

**This is a genuine Android source project (Java + Android SDK + SQLite), not a browser website or a PWA.**
No ChatGPT login is needed to run the compiled app. The current version stores data **locally on one Android phone**, works offline, and has been prepared for a debug APK build using GitHub Actions or Android Studio.

> **Important:** An installable APK is NOT bundled. Android SDK/Gradle build tools were not available in the creation environment; the full Android build and device/UI test have not yet been performed. Do not use for live warehouse transactions without independent testing, backup, and authorization.

## Included functions

- Separate DP Cocoa (**Tally**) and Mondelez (**SAP**) warehouse views; **not connected to SAP/Tally APIs**.
- 1,372 starting locations: **A01–K84 (DP Cocoa)**, **L01–P84 (Mondelez)** and **Q01–Q28 (shared)**. Admin may add floor/temporary racks manually.
- Product master with Bags / Boxes and kg per unit; Admin can update the product-wide UOM.
- Putaway by product, batch, quantity, rack, FGTN/remarks, manufacturing date; matching-batch or vacant-rack suggestion. Supports one-shot atomic **multiple rack putaway** (`A01=20, A02=35`) of the same product/batch.
- Automatic first-in-first-out dispatch recommendations by manufacturing date, else DYYMMDD batch code, else received date; dispatch only from QA **RELEASE**, reduces rack stock in a transaction.
- QA **HOLD / RELEASE / REJECT**, no separate sign-off screen.
- Rack relocation / audit re-slotting with stock history.
- Physical count separate from system stock; Admin/Manager approval/rejection before correcting live stock; no silent stock edits.
- Assign whole racks to users for cycle counting (prevents duplicate active assignments).
- Admin / Manager / Operator / Checker / Viewer roles. Viewer may search and export but not edit.
- Personal admin recovery code configured at first setup, salted PBKDF2 password hashes; Admin creates/resets local users.
- Monthly putaway/dispatch totals, today's calculated opening, batches, quality hold status.
- Reports: complete stock, product summary, batch summary, today's opening, putaway, dispatch, aging, cycle counts, audit.
- Real **.xlsx** export (for stock: separate `Product Summary` and `Rack Data` sheets), A4-landscape PDF, one-rack PDF, all through Android file picker.
- Bulk CSV receiving import for Admin, with explicit confirmation that it adds quantities, not replaces stock.
- Local change outbox for planned server sync, **but the sync process is NOT connected yet**.

## Planned, not yet delivered

- **Online/offline automatic sync across different phones**, central login & permissions (requires a secure server, account hosting, backups and reconciliation).
- Direct SAP/Tally data connection and two-way reconciliation (the ERP fields/companies are represented, not wired to external ERPs).
- Import arbitrary prior Excel rack data (CSV import is available, .xlsx import still needs development and format mapping).
- ERP invoice validation, automatic periodic cloud backups, offline conflict prevention between devices, push notifications.
- Android production-signed APK / Google Play Store release and full device testing.
- Remote admin revocation across phones, SMS/OTP recovery, automatic rack capacity and expiry alerts.

## Build a debug APK with Android Studio

1. Install Android Studio on Windows/Mac/Linux, open this project folder.
2. Android Studio will sync Android Gradle Plugin **8.13.0**, Gradle **8.13**, compile SDK **36** with JDK **17**.
3. Build > Build APK(s), or execute `gradle :app:assembleDebug` with Gradle 8.13 available.
4. Install `app/build/outputs/apk/debug/app-debug.apk` on Android for private testing.

## Build from an Android phone (cloud build)

This repository includes `.github/workflows/android-apk.yml`. After putting **all source files in the matching folder structure** into a **private GitHub repository**, the workflow runs when the `main` branch is updated or started manually under **Actions → Build Android APK → Run workflow**. Download the `DP-Cocoa-FG-Rack-debug-APK` build artifact and extract `app-debug.apk` on Android to install it. This may require enabling Android's one-time permission to install apps from that source. A GitHub account and internet connection are required only for the APK build; daily offline use does not need internet.

**Important:** A repository created from just the .zip attachment as a single file will NOT build: extract and upload the actual project files while preserving folders. Never upload actual warehouse data, passwords or recovery codes into a public repository. Cloud builds are subject to GitHub's service quotas and policies.

## First login (no universal backdoor password)

On the first start, choose your Admin username, a strong password (10+ characters), and a separate private recovery code (12+ characters). Create Operator, Checker, Manager and Viewer users from the Admin control panel. **No common master key (including previously mentioned sample passwords) is hardcoded in app source**; otherwise anyone reverse-engineering the APK could bypass security. A recovery code only works on the device where it was enrolled.

## Import receiving CSV

Use `Admin Settings → Import Putaway CSV`. Fields must be in this exact order:

```csv
org,product,batch,rack,qty,mfg_date,qa,notes
DP,Cocoa Powder 30,D2609030J0,A01,25,2026-09-03,HOLD,Test only
MDLZ,Cocoa Mass Indian,D260901000,L01,10,2026-09-01,RELEASE,Test only
```

Products and racks must already be configured. For custom raw-material or finished-product sizes, set each product's kg per unit (example: 25 / 450 / 1200 kg as appropriate for your actual product). This is only an illustrative **test CSV**, not a statement of real stock. Importing again ADDS quantities again. If any row is bad, other successful rows stay imported; correct and reimport only failed rows.

## Essential cautions

- This first version is **single-device offline** and is **not yet an authoritative shared multi-phone inventory system**. Do not use independent copies to dispatch the same stock; those phones would not see one another's changes.
- App uninstall / clearing storage deletes local SQLite data. `.xlsx` reports are **not a full database restore backup**.
- Test with dummy data before loading company records. Verify FIFO against real batch formats, all QA statuses, opening balance, and UOM conventions.
- Private role/login rules are implemented in the Android interface; they need server-side enforcement once multi-user online sync is added.

## Project files

- `app/src/main/java/com/dpcocoa/fgrack/MainActivity.java`: native UI, logins, workflows, import/export
- `WarehouseDb.java`: SQLite schema, rack seed, stock movement & audit transactions
- `FifoEngine.java`: dependency-free FIFO selection algorithm
- `ExportKit.java`: valid Excel XLSX and PDF exporters, no external libraries
- `.github/workflows/android-apk.yml`: cloud build pipeline
- `docs/SYNC_ROADMAP.md`: plan for safe offline multi-device synchronization
- `FIFO_TEST.java`: self-contained unit test of FIFO selection
