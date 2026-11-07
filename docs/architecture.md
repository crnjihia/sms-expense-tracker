# Architecture & System Design

SMS Expense Tracker is built following modern Android Clean Architecture and Unidirectional Data Flow (MVI/UDF) principles with strict on-device data sovereignty (zero network permissions).

---

## High-Level Architecture Diagram

```mermaid
flowchart TD
    subgraph System ["Android System & Telephony"]
        SMS["Incoming SMS Broadcast"]
        SAF["Storage Access Framework"]
        BIO["BiometricPrompt API"]
    end

    subgraph Ingestion ["Ingestion & Background Pipeline"]
        BCR["SmsBroadcastReceiver"]
        WM["WorkManager"]
        ING["SmsIngestionEngine"]
        PARSER["TemplateSmsParser"]
        CW["CategorisationWorker"]
        BW["BudgetCheckWorker"]
    end

    subgraph Data ["Local Storage Layer"]
        ROOM["Room Database (AppDatabase v2)"]
        TDAO["TransactionDao"]
        BDAO["BudgetDao"]
        CDAO["CategoryDao"]
        RDAO["RuleDao"]
        SDAO["SmsSourceStateDao"]
        PREFS["Encrypted / SharedPreferences"]
    end

    subgraph Domain ["Repository & Domain Logic"]
        TREPO["TransactionRepository"]
        BREPO["BudgetRepository"]
        CREPO["CategorisationRepository"]
        CSV["CsvExportManager"]
    end

    subgraph UI ["Jetpack Compose & Material 3"]
        NAV["NavGraph (Navigation Compose)"]
        HVM["HomeViewModel"]
        TVM["TransactionViewModel"]
        BVM["BudgetViewModel"]
        RVM["ReportsViewModel"]
        SVM["SettingsViewModel"]
        IVM["ManualImportViewModel"]
    end

    SMS -->|android.provider.Telephony.SMS_RECEIVED| BCR
    BCR --> WM
    WM --> CW
    WM --> BW
    WM --> ING
    ING --> PARSER
    PARSER -->|ParsedSms| CREPO
    CREPO --> TDAO
    TDAO --> ROOM
    BDAO --> ROOM
    CDAO --> ROOM
    RDAO --> ROOM
    SDAO --> ROOM

    ROOM --> TREPO
    ROOM --> BREPO
    ROOM --> CREPO

    TREPO --> HVM
    TREPO --> TVM
    TREPO --> RVM
    BREPO --> HVM
    BREPO --> BVM
    CREPO --> TVM

    HVM --> NAV
    TVM --> NAV
    BVM --> NAV
    RVM --> NAV
    SVM --> NAV
    IVM --> NAV

    SAF <-->|RFC 4180 CSV| CSV
    BIO <-->|Authentication Gate| SVM
```

---

## 1. Zero Network & Privacy Boundary

The application deliberately excludes `android.permission.INTERNET` from `AndroidManifest.xml`.
- **Zero External Telemetry:** No Firebase, Google Analytics, Sentry, or third-party ad networks.
- **On-Device Parsing:** All SMS parsing and categorisation happen completely on the device's CPU.
- **Backup Exclusion:** `backup_rules.xml` and `data_extraction_rules.xml` explicitly exclude databases, shared preferences, and cache directories from cloud and device-to-device adb transfers.
- **Biometric Security:** Biometric authentication gates app access on cold-start and resume when enabled.

---

## 2. Parsing Pipeline (`TemplateSmsParser`)

The parsing engine applies a precedence-ordered set of regex templates against incoming SMS sender headers and normalized body text.

### Supported Senders & Formats:
1. **M-Pesa (`MPESA`, `M-PESA`):**
   - Person-to-Person (P2P) transfers (Sent & Received)
   - Paybill transactions (e.g., KPLC, Nairobi Water)
   - Buy Goods / Till transactions (e.g., Naivas, Carrefour)
   - Cash withdrawals via M-Pesa agents
   - Airtime purchases
   - Transaction reversals
2. **Equity Bank (`EQUITY`, `EQUITYBANK`, `EQUITYBC`):**
   - EazzyPay merchant card debits
   - Account debits with balance tracking
   - Account credits with reference codes
3. **KCB Bank (`KCB`, `KCBBANK`, `KCBGroup`):**
   - Point-of-Sale (POS) merchant spending
   - Account debits and credits

---

## 3. Categorisation Engine Precedence

When transactions are imported or backfilled, category assignment follows a strict 3-tier precedence hierarchy:

```mermaid
flowchart TD
    TXN["Uncategorised Transaction"] --> CHECK_OVERRIDE{"1. User Manual Override?"}
    CHECK_OVERRIDE -- Yes --> KEEP_OVERRIDE["Keep User's Category (Never overwrite)"]
    CHECK_OVERRIDE -- No --> CHECK_RULES{"2. User Custom Rules (RuleDao)?"}
    CHECK_RULES -- Yes --> APPLY_RULE["Assign Rule Category (Matched by Keyword)"]
    CHECK_RULES -- No --> CHECK_DEFAULTS{"3. Built-in Merchant Keywords?"}
    CHECK_DEFAULTS -- Match --> APPLY_DEFAULT["Assign Default Category (e.g. Groceries, Utilities)"]
    CHECK_DEFAULTS -- No Match --> UNCAT["Leave Uncategorised (null)"]
```

---

## 4. Room Database Schema (Version 2)

- **`transactions` table:**
  - `id`: Primary key (autoincrement)
  - `amount`: KES integer in whole shillings (negative for debits, positive for credits)
  - `timestamp`: UTC ISO-8601 `OffsetDateTime`
  - `description`: Clean counterparty or merchant name
  - `raw_sms_body`: Original text for debugging / auditing
  - `sender`: Sender identifier (e.g. `MPESA`)
  - `category_id`: Foreign key referencing `categories.id` (`ON DELETE SET NULL`)
  - `source_state_id`: Foreign key referencing `sms_source_states.id` (`ON DELETE CASCADE`)
- **`budgets` table:**
  - `id`: Primary key
  - `category_id`: Foreign key referencing `categories.id` (`ON DELETE CASCADE`)
  - `month`: String in format `YYYY-MM` (e.g. `2024-08`)
  - `limit_ksh`: Target budget threshold in KES
- **`categories` table:**
  - `id`: Primary key
  - `name`: Unique category name
  - `icon`: Icon identifier or emoji
  - `is_default`: Boolean
- **`categorisation_rules` table:**
  - `id`: Primary key
  - `category_id`: Foreign key referencing `categories.id` (`ON DELETE CASCADE`)
  - `keyword`: Normalized lowercase keyword to match against transaction descriptions

---

## 5. UI Architecture & Design System

- **Framework:** Jetpack Compose + Material 3 Design System.
- **State Management:** Unidirectional Data Flow via Kotlin `StateFlow` and Compose `collectAsStateWithLifecycle()`.
- **Navigation:** Single-Activity Compose navigation with typed destination routes:
  - `Screen.Home`
  - `Screen.Transactions`
  - `Screen.Budgets`
  - `Screen.Reports`
  - `Screen.Settings`
  - `Screen.ManualImport`
  - `Screen.RuleList` & `Screen.RuleEdit`
  - `Screen.BudgetEdit`
- **Charts:** Bespoke hardware-accelerated Compose `Canvas` implementations (monthly trend bar chart and category breakdown donut chart) with TalkBack accessibility labels and contrast guarantees ($\ge 4.5:1$).

