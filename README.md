# Moje Finance

Android aplikacija za beleženje osebnih financ (Kotlin + Jetpack Compose + Room).

## Zagon

1. Odpri mapo v **Android Studio** (Hedgehog ali novejši).
2. Pusti, da Gradle sinhronizira (wrapper se generira ob prvem zagonu, ali poženi `gradle wrapper`).
3. Zaženi na napravi/emulatorju (minSdk 26).

## Funkcije

| Funkcija | Kje | Opombe |
|---|---|---|
| Glasovni vnos s fiksnimi pravili | `feature/voice/` + `domain/voice/` | SpeechRecognizer (sl-SI) + lastni parser, offline |
| Widget na domačem zaslonu | `feature/widget/` | Glasovni vnos / hiter strošek / hiter prihodek |
| Quick Settings tile | `feature/quickadd/` | Deluje tudi z zaklenjenega zaslona (po odklepu) |
| Branje bančnih obvestil | `feature/notifications/` | Ustvari nepotrjene transakcije; vzorce prilagodi svoji banki |
| OCR računov | `feature/ocr/` | ML Kit, najde skupni znesek |
| Ponavljajoče transakcije | `feature/recurring/` | Dnevni WorkManager; avtomatski zapis ali opomnik |
| Uvoz mmbackup | `data/import/` | Mapira račune, kategorije, transakcije, transferje |
| Statistika po kategorijah | `ui/stats/` | Mesečni pregled |

## Glasovni ukazi (fiksna gramatika)

```
[tip] [kategorija] [znesek] [opomba ...] [datum]
```

- **tip**: „dohodek"/„prihodek" → prihodek; sicer se sklepa iz kategorije (privzeto strošek)
- **znesek**: `12,30` · `12.30` · `12 30` · „12 evrov 30" · „dvanajst evrov trideset" · „tisoč dvesto"
- **datum**: „danes" (privzeto) · „včeraj" · „predvčerajšnjim"
- **kategorija**: ujemanje po nazivu ali ključnih besedah kategorije

Primeri: „malica 4,50" · „bencin 30 evrov včeraj" · „dohodek plača 1200" · „kava dve evri petdeset pri Toniju"

Parser je pokrit s testi (10 slovenskih primerov). Ključne besede kategorij urejaš v podatkih kategorije (`keywords`).

## Uvoz iz mmbackup

`.mmbackup` = ZIP (z 8 bajti glave) z `MyFinance.db` (SQLite). Mapiranje:

| mmbackup | Moje Finance |
|---|---|
| `account` | `accounts` (prazen title → „Glavni račun") |
| `category` (type Expense/Income) | `categories` (EXPENSE/INCOME) |
| `transaction` + `sync_link` | `transactions` (zneski v centih, veza na kategorijo/račun) |
| `transfer` | `transfers` |
| `isRemoved=1` | preskočeno |
| `reminding` | (se ne uvaža — ponavljajoča pravila ustvariš ročno) |

Originalni `uid`-ji se ohranijo → **ponovni uvoz je idempotenten** (upsert, brez dvojnikov).

## Arhitektura

- **Lokalna baza**: Room (SQLite). Vse entitete so sync-ready: `uid` (UUID), `createdAt`/`updatedAt`, `deleted` (soft delete).
- **DI**: ročni `AppContainer` (brez Hilta).
- **UI**: Compose, en `MainViewModel` + ločeni zasloni.

## Cloud sync (faza 2)

Vmesnik je pripravljen v `sync/SyncContract.kt` (push/pull, "last write wins" po `updatedAt`).
Predlagan backend: **Supabase** — enake tabele + `user_id` stolpec (RLS), Realtime za žive posodobitve.
Ker so ključi že UUID-ji, migracija ne zahteva sprememb sheme.

## Prilagoditve

- **Bančna obvestila**: regex vzorci v `BankNotificationListenerService.PATTERNS` — prilagodi besedilu obvestil tvoje banke.
- **Dovoljenja**: mikrofon (glas), kamera (OCR), dostop do obvestil (Nastavitve → posebni dostop).
