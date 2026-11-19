# AGENTS.md

Kontekst dla agentów AI (Claude Code, Copilot, Cursor itp.) pracujących w tym repozytorium.
Plik jest celowo zakomitowany: projekt powstaje z pomocą AI, ale decyzje projektowe
i odpowiedzialność za kod należą do autora. Agent pomaga, a człowiek decyduje i weryfikuje.

## Domena

Serwis REST do zarządzania kuponami rabatowymi (zadanie rekrutacyjne Empik).

### Funkcjonalności
1. Utworzenie kuponu (`POST /api/v1/coupons`) – bez uwierzytelniania (zgodnie z treścią zadania).
2. Rejestracja użycia kuponu przez użytkownika (`POST /api/v1/coupons/{code}/redemptions`).

### Kupon
Unikalny kod, data utworzenia, maksymalna liczba użyć, bieżąca liczba użyć, kraj (ISO 3166-1 alpha-2).

### Reguły biznesowe (niezmienniki – nie wolno ich osłabiać)
- **BR-1** Kod jest unikalny i case-insensitive (`WIOSNA` == `wiosna`). Przechowywany znormalizowany:
  `trim().toUpperCase(Locale.ROOT)`, format `^[A-Z0-9_-]{3,32}$`.
- **BR-2** Liczba użyć nigdy nie przekracza `maxUses` – również przy wielu instancjach i równoległych żądaniach
  („kto pierwszy ten lepszy”).
- **BR-3** Kupon może użyć tylko klient z kraju kuponu, ustalonego na podstawie adresu IP.
- **BR-4** Jeden użytkownik (`userId` w requeście) może użyć danego kuponu tylko raz.
- **BR-5** Każda odmowa zwraca czytelny błąd (RFC 9457 `ProblemDetail` + `errorCode`):

| Sytuacja | HTTP | `errorCode` |
|---|---|---|
| Kupon nie istnieje | 404 | `COUPON_NOT_FOUND` |
| Kraj spoza kuponu / nieustalony | 403 | `COUNTRY_NOT_ALLOWED` |
| Użytkownik już użył kuponu | 409 | `COUPON_ALREADY_REDEEMED` |
| Limit użyć wyczerpany | 409 | `COUPON_EXHAUSTED` |
| Kod już istnieje (tworzenie) | 409 | `COUPON_ALREADY_EXISTS` |
| Usługa geolokalizacji niedostępna | 503 | `GEOLOCATION_UNAVAILABLE` |
| Niepoprawne dane wejściowe | 400 | `VALIDATION_FAILED` |

## Kluczowe decyzje techniczne (nie zmieniać bez uzgodnienia z autorem)
- BR-2 gwarantuje baza: w jednej transakcji `INSERT … ON CONFLICT (coupon_id, user_id) DO NOTHING`,
  potem `UPDATE coupon SET current_uses = current_uses + 1 WHERE id = ? AND current_uses < max_uses`.
  **Zakazane:** read-check-write w Javie, `synchronized`/locki w JVM, `@Version` dla licznika użyć.
- Unikalność wymuszają ograniczenia DB (`uq_coupon_code`, `uq_redemption_coupon_user`, `CHECK`), nie „sprawdź, potem zapisz”.
- Geolokalizacja jest wywoływana **poza** transakcją DB, z timeoutami i polityką fail-closed.
- IP klienta pochodzi z `request.getRemoteAddr()`. Nie czytamy ręcznie `X-Forwarded-For`.
- Architektura: package-by-feature `coupon` → `domain` / `application` / `infrastructure` (`web`, `persistence`, `geo`).
  `domain` nie zależy od Springa ani JPA.

## Stack
Java 25, Spring Boot 4.1.2, PostgreSQL 17, Flyway, Spring Data JPA, Caffeine, springdoc-openapi,
JUnit 5 + AssertJ + Mockito, Testcontainers, WireMock. Build: Maven wrapper.

## Komendy
- Pełny build z testami: `./mvnw verify` (wymaga Dockera – Testcontainers)
- Same testy jednostkowe: `./mvnw test`
- Lokalne uruchomienie: `docker compose up --build`

## Zasady pracy agenta
- **TDD:** najpierw test, który nie przechodzi, potem minimalna implementacja.
- `*Test` to testy szybkie, bez kontenerów. `*IT` to testy integracyjne z Testcontainers.
- Każda zmiana reguły BR-1…BR-5 wymaga testu. Zmiany w redemption wymagają zielonego `ConcurrentRedemptionIT`.
- Nie dodawaj zależności, warstw ani wzorców „na zapas” (YAGNI). Proponuj je autorowi, nie wprowadzaj sam.
- Nie używaj H2 ani mocków bazy w testach persystencji.
- Kod, nazwy i commity po angielsku, w stylu Conventional Commits.
- Agent **nie commituje i nie pushuje** samodzielnie. Autor przegląda każdy diff i sam tworzy commit.
- Nie czytaj ani nie twórz plików z sekretami (`.env`, klucze API).
