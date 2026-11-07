<!-- path: docs/adr/001-on-device-encryption-sqlcipher.md -->
# ADR-001: On-Device Data Encryption with Room and SQLCipher

## Context & Problem Statement

SMS Expense Tracker operates entirely on-device, processing sensitive Kenyan financial SMS messages (M-Pesa, Equity Bank, KCB). Although M-Pesa PINs are never included in SMS receipts, transaction text contains:
- Total account balances (`...new M-PESA balance is Ksh 45,200.00...`)
- Full names, phone numbers, and paybill/till numbers of counterparties
- Exact timestamps and spending habits

While Android OS provides File-Based Encryption (FBE) under credential-encrypted storage when the device screen is locked, mobile devices face distinct physical and forensic threat vectors:
1. Rooted devices where root processes inspect `/data/data/com.example.smsexpensertracker/databases/`.
2. Physical device loss with unlocked screen or ADB debugging enabled.
3. Cold-boot and memory extraction attacks.

We need a clear architectural blueprint to integrate application-level database encryption using **SQLCipher for Android** with **Android Keystore**, detailing the migration strategy, performance trade-offs, and key management.

## Decision Drivers

- **Zero Network Boundary**: No credentials or keys may ever leave the device.
- **Hardware-Backed Security**: Cryptographic keys must be bound to the device's Secure Element (SE) or Trusted Execution Environment (TEE) / StrongBox.
- **Room Compatibility**: Seamless integration with existing Room 2.6.x entities, DAOs, and migrations.
- **Performance**: High frame rate in Jetpack Compose UI without blocking main threads on large queries.

## Architecture & Integration Plan

### 1. Hardware-Backed Key Derivation via Android Keystore

The encryption passphrase for SQLCipher is never stored as plaintext in SharedPreferences or disk. Instead, an AES-256 GCM master key is generated inside the Android Keystore:

```kotlin
val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
if (!keyStore.containsAlias(KEY_ALIAS)) {
    val keyGenerator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        "AndroidKeyStore"
    )
    val spec = KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setUserAuthenticationRequired(false) // Or true when Biometric-bound
        .build()
    keyGenerator.init(spec)
    keyGenerator.generateKey()
}
```

A 256-bit random byte array is generated via `SecureRandom`, encrypted with the Keystore master key, and stored in private app storage. On database open, the passphrase is decrypted in RAM, passed to SQLCipher, and immediately zeroed from memory.

### 2. SQLCipher Room Integration

Add dependency to `gradle/libs.versions.toml`:
```toml
sqlcipher-android = { module = "net.zetetic:sqlcipher-android", version = "4.5.5" }
sqlite-ktx = { module = "androidx.sqlite:sqlite-ktx", version = "2.4.0" }
```

Configure Room's `RoomDatabase.Builder` with `SupportFactory`:
```kotlin
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

val passphraseBytes: ByteArray = deriveKeyFromKeystore()
val factory = SupportOpenHelperFactory(passphraseBytes)

Room.databaseBuilder(context, AppDatabase::class.java, "sms_expense_tracker.db")
    .openHelperFactory(factory)
    .addMigrations(MIGRATION_1_2)
    .build()
```

### 3. In-Place Plaintext to Encrypted Migration

For devices upgrading from the unencrypted Room database to SQLCipher:

1. Open existing plaintext database with standard SQLite.
2. Attach a new encrypted database file:
   ```sql
   ATTACH DATABASE 'sms_expense_tracker_encrypted.db' AS encrypted KEY 'x''...hex-passphrase...''';
   SELECT sqlcipher_export('encrypted');
   DETACH DATABASE encrypted;
   ```
3. Delete the old plaintext database file securely.
4. Rename `sms_expense_tracker_encrypted.db` to `sms_expense_tracker.db`.

## Trade-offs & Consequences

### Positive
- **Complete At-Rest Encryption**: 256-bit AES-CBC encryption per 4096-byte database page (including headers, indices, and write-ahead logs).
- **Protection Against Forensic Tools**: Direct filesystem dumps yield only pseudo-random ciphertext.
- **Clean Room Synergy**: DAOs, Flow queries, and Room migrations operate identically without changes to business logic.

### Negative / Mitigations
- **Binary Size**: Adds ~4.2 MB across ABI architectures (`arm64-v8a`, `armeabi-v7a`, `x86_64`) due to native `libsqlcipher.so` binaries.
- **CPU / Query Overhead**: ~10% to 15% increase in SQLite read/write latency due to page-level decryption. Mitigated by Room's background Dispatchers (`Dispatchers.IO`) and paging.
- **Key Recovery**: If Android Keystore state is corrupted by an OS factory reset, data cannot be recovered. This is an intentional security design choice consistent with our zero-cloud policy.

## Implementation Status

- **Phase 7 State**: Optional integration path documented in full detail with Keystore derivations.
- **Production Switch**: Can be toggled on by adding `net.zetetic:sqlcipher-android` and enabling `openHelperFactory` in `DatabaseModule.kt`.

