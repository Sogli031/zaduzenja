# Podvrste rukavica i veličine pri zaduženju

## Sažetak

Dva nova zahteva:
1. MaxiCut rukavice kao podvrsta sa periodom od 2 meseca (obične ostaju 1 mesec)
2. Unos veličine/broja pri zaduženju cipela, odela i majice

---

## Data model

### ArticleType enum — nova vrednost

```kotlin
MAXICUT("MaxiCut rukavice", 2, "maxicut")
```

### HistoryEntry — nova klasa

Zamenjuje `List<LocalDate>` u istoriji:

```kotlin
data class HistoryEntry(
    val date: LocalDate,
    val size: String? = null
)
```

### ArticleRecord — proširenje

```kotlin
data class ArticleRecord(
    val lastAssignment: LocalDate? = null,
    val history: List<HistoryEntry> = emptyList(),
    val lastSize: String? = null  // poslednja korišćena veličina
)
```

### Predefinisane veličine

Mapa po ArticleType:
- CIPELE: "39", "40", "41", "42", "43", "44", "45", "46"
- ODELO: "48", "50", "52", "54", "56", "58"
- MAJICA: "S", "M", "L", "XL", "XXL", "XXXL"
- RUKAVICE / MAXICUT: null (bez veličina)

### JSON format

```json
{
  "rukavice": {
    "last_assignment": "2025-01-15",
    "history": [{"date": "2025-01-15"}]
  },
  "maxicut": {
    "last_assignment": null,
    "history": []
  },
  "cipele": {
    "last_assignment": "2025-01-15",
    "history": [{"date": "2025-01-15", "size": "43"}]
  },
  "odelo": {
    "last_assignment": "2025-03-01",
    "history": [{"date": "2025-03-01", "size": "52"}]
  },
  "majica": {
    "last_assignment": "2025-02-01",
    "history": [{"date": "2025-02-01", "size": "XL"}]
  }
}
```

### Kompatibilnost unazad

Parsiranje istorije prihvata oba formata:
- Stari: `["2025-01-15", "2025-02-15"]` (niz stringova)
- Novi: `[{"date": "2025-01-15", "size": "43"}]` (niz objekata)

Pri čitanju starog formata, konvertuje se u `HistoryEntry(date, size=null)`.

---

## UI izmene

### Glavna kartica "Rukavice"

Jedna kartica sa dva reda razdvojena divider-om:
- **Rukavice (obične)** — status, datumi, dugme ZADUŽI (1 mesec)
- **MaxiCut rukavice** — status, datumi, dugme ZADUŽI (2 meseca)

Svaki red ima svoj status indikator (zeleno/crveno) i nezavisno dugme.

### Dijalog za izbor veličine

Prikazuje se pri zaduženju cipela, odela i majice:
- Naslov: "Zaduži [tip]"
- Horizontalni čipovi (Chips) sa predefinisanim veličinama
- Poslednja korišćena veličina unapred selektovana
- Dugme POTVRDI (disabled bez selekcije) + OTKAŽI

Rukavice i MaxiCut — bez dijaloga, direktno zaduženje.

### Ekran istorije

- Prikaz veličine pored datuma: "15.01.2025 — br. 43"
- Bez veličine: samo datum
- Filter dobija novu opciju "MaxiCut rukavice"

---

## Notifikacije

MaxiCut se dodaje u dnevnu proveru. Ako su i obične i MaxiCut spremne, obe se pominju u tekstu notifikacije.

---

## Google Drive backup

Bez promena u logici. Novi JSON format se automatski sinhronizuje. Restore starog backup-a podržan kroz kompatibilno parsiranje.

---

## Fajlovi za izmenu

| Fajl | Izmene |
|------|--------|
| `Data.kt` | `HistoryEntry` klasa, `MAXICUT` enum, prošireni `ArticleRecord`, predefinisane veličine, `attemptAssignment` sa size parametrom, kompatibilno parsiranje |
| `App.kt` | Dvostruki red u kartici rukavica, dijalog za veličinu, prikaz veličine u istoriji |
| `Notifications.kt` | MaxiCut u proveri dostupnih stavki |
| `assets/z.json` | Dodati `"maxicut"` ključ |
