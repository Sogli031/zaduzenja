# Project Map
_Generated: 2026-05-12 22:44 | Staleness: timestamps_

## Directory Structure
android/ — Android Compose aplikacija za zaduženja, artikle, rokove, istoriju, notifikacije i backup.
android/app/src/main/java/com/zaduzenja/app/ — glavni app, data repository, notifikacije, tema i UI root.
android/app/src/main/java/com/zaduzenja/app/data/db/ — Room baza, DAO, entiteti i JSON-to-Room migracija.
android/app/src/main/java/com/zaduzenja/app/data/backup/ — Google Drive backup.
android/app/src/test/ — unit testovi za notifikacije i raspored.
docs/plans/ — lokalni planovi, uključujući podvrste i veličine.

## Key Files
android/settings.gradle.kts — definiše Gradle root `Zaduzenja`.
android/build.gradle.kts — Android/Kotlin/Compose/KSP plugin verzije.
android/app/build.gradle.kts — Compose, Room/KSP i backup konfiguracija.
android/app/src/main/AndroidManifest.xml — notification, internet, boot/time receiver-i i launcher.
android/app/src/main/java/com/zaduzenja/app/App.kt — Compose root, screen state i glavni UI tok.
android/app/src/main/java/com/zaduzenja/app/Data.kt — tipovi artikala, veličine, istorija, repository i JSON import/export.
android/app/src/main/java/com/zaduzenja/app/Notifications.kt — pravila i scheduling notifikacija dostupnosti.
android/app/src/main/java/com/zaduzenja/app/data/db/AppDatabase.kt — Room database.
android/app/src/main/java/com/zaduzenja/app/data/db/JsonToRoomMigration.kt — migracija starog JSON stanja.
android/app/src/main/java/com/zaduzenja/app/data/backup/GoogleDriveBackup.kt — Google Drive sync.
android/app/src/test/java/com/zaduzenja/app/ShiftNotificationScheduleTest.kt — regresiona zaštita rasporeda prve smene.

## Critical Constraints
- Poštovati parent `AGENTS.md`: lokalno bez Git komandi.
- Gradle komande se pokreću iz `zaduzenja/android`, ne iz korena `zaduzenja`.
- Notifikacije prve smene imaju stroga pravila prozora i ciklusa; svaku izmenu `Notifications.kt` proveriti boundary testovima.
- Room i JSON backup/migracija moraju ostati kompatibilni pri promenama `ArticleType`, veličina ili istorije.

## Hot Files
android/app/src/main/java/com/zaduzenja/app/Data.kt, android/app/src/main/java/com/zaduzenja/app/Notifications.kt, android/app/src/main/java/com/zaduzenja/app/App.kt, android/app/src/main/java/com/zaduzenja/app/data/db/AppDatabase.kt, android/app/src/main/java/com/zaduzenja/app/data/backup/GoogleDriveBackup.kt
