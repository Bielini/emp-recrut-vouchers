# Vouchers – serwis kuponów rabatowych

REST-owy serwis do tworzenia kuponów rabatowych i rejestrowania ich użycia. Największy nacisk położyłem na
to, żeby **limit użyć nie dał się przekroczyć przy wielu instancjach i równoległych żądaniach** (szczyt typu
Black Friday) i żeby każda odmowa była dla klienta czytelna.

**Stack:** Java 25, Spring Boot 4.1, PostgreSQL 17, Flyway, Spring Data JPA, Caffeine, springdoc-openapi,
JUnit 5 + AssertJ + Mockito, Testcontainers, WireMock, JaCoCo, Docker, GitHub Actions.

## Spis treści
1. [Wymagania i reguły biznesowe](#1-wymagania-i-reguły-biznesowe)
2. [Uruchomienie](#2-uruchomienie)
3. [API](#3-api)
4. [Założenia](#4-założenia)
5. [Decyzje projektowe](#5-decyzje-projektowe)
6. [Współbieżność – jak działa i jak to przetestowałem](#6-współbieżność--jak-działa-i-jak-to-przetestowałem)
7. [Skalowanie na Black Friday](#7-skalowanie-na-black-friday)
8. [Bezpieczeństwo](#8-bezpieczeństwo)
9. [Co dalej](#9-co-dalej)
10. [Praca z AI](#10-praca-z-ai)

## 1. Wymagania i reguły biznesowe

Kupon ma unikalny kod, datę utworzenia, maksymalną i bieżącą liczbę użyć oraz kraj (ISO 3166-1 alpha-2).

| # | Reguła | Gdzie jest egzekwowana |
|---|---|---|
| BR-1 | Kod jest unikalny i nie rozróżnia wielkości liter (`WIOSNA` == `wiosna`) | normalizacja w `CouponCode` + `UNIQUE` i `CHECK (code = UPPER(code))` w bazie |
| BR-2 | Liczba użyć nigdy nie przekracza limitu, także przy wielu instancjach | atomowy warunkowy `UPDATE` + `CHECK (current_uses BETWEEN 0 AND max_uses)` |
| BR-3 | Kupon może użyć tylko klient z kraju kuponu (na podstawie IP) | `CountryResolver` (port) + adapter ip-api.com, fail-closed |
| BR-4 | Jeden użytkownik może mieć tylko jedno aktywne użycie danego kuponu | częściowy `UNIQUE (coupon_id, user_id) WHERE reversed_at IS NULL` + `INSERT … ON CONFLICT DO NOTHING` |
| BR-5 | Każda odmowa zwraca czytelny błąd | RFC 9457 `ProblemDetail` z polem `errorCode` |
| BR-6 | Użycie można wycofać (anulowane/zwrócone zamówienie), co zwalnia pulę dla innych klientów | miękkie usunięcie (`reversed_at`) + warunkowy `UPDATE … current_uses - 1` w jednej transakcji |

## 2. Uruchomienie

Wymagany jest tylko Docker. Java 25 jest potrzebna jedynie do uruchamiania bez kontenera.

```bash
# Aplikacja + PostgreSQL w kontenerach (http://localhost:8080)
docker compose up --build

# Pełny build: testy jednostkowe, integracyjne (Testcontainers) i próg pokrycia JaCoCo
./mvnw verify

# Lokalnie z IDE/terminala; spring-boot-docker-compose sam podnosi PostgreSQL z compose.yaml
./mvnw spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Raport pokrycia po `verify`: `target/site/jacoco/index.html`

> **Geolokalizacja lokalnie.** W Dockerze adres klienta jest prywatny (sieć bridge), więc prawdziwa
> geolokalizacja zawsze zwróciłaby „kraj nieznany”. Dlatego `compose.yaml` ustawia `GEO_PROVIDER=FIXED`
> i `GEO_FIXED_COUNTRY=PL`: każdy klient jest traktowany jak klient z Polski. Domyślnie (bez tych zmiennych)
> działa adapter ip-api.com.

### Konfiguracja

| Zmienna | Domyślnie | Opis |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/vouchers`, `vouchers`, `vouchers` | połączenie z bazą |
| `DB_POOL_SIZE` | `20` | rozmiar puli Hikari |
| `GEO_PROVIDER` | `IP_API` | `IP_API` albo `FIXED` (tylko lokalnie) |
| `GEO_FIXED_COUNTRY` | – | kraj zwracany przez `FIXED` |
| `GEO_BASE_URL` | `http://ip-api.com` | adres providera |
| `GEO_CONNECT_TIMEOUT`, `GEO_READ_TIMEOUT` | `500ms`, `1s` | timeouty wywołania geolokalizacji |
| `GEO_CACHE_TTL`, `GEO_CACHE_MAX_SIZE` | `1h`, `100000` | cache wyników geolokalizacji |

## 3. API

### Utworzenie kuponu
```bash
curl -i -X POST localhost:8080/api/v1/coupons \
  -H "Content-Type: application/json" \
  -d '{"code":"wiosna","maxUses":10,"countryCode":"pl"}'
```
`201 Created`, nagłówek `Location: /api/v1/coupons/WIOSNA`:
```json
{"code":"WIOSNA","maxUses":10,"currentUses":0,"countryCode":"PL","createdAt":"2026-11-24T10:00:00Z"}
```

### Odczyt kuponu
```bash
curl -i localhost:8080/api/v1/coupons/Wiosna
```

### Użycie kuponu
```bash
curl -i -X POST localhost:8080/api/v1/coupons/wiosna/redemptions \
  -H "Content-Type: application/json" \
  -d '{"userId":"user-1"}'
```
`201 Created`:
```json
{"couponCode":"WIOSNA","userId":"user-1","redeemedAt":"2026-11-24T10:00:05Z"}
```

### Wycofanie użycia (anulowane lub zwrócone zamówienie)
```bash
curl -i -X DELETE localhost:8080/api/v1/coupons/wiosna/redemptions/user-1
```
`204 No Content`. Użycie wraca do puli, a wiersz zostaje w historii z `reversed_at`. Po wycofaniu ten sam
użytkownik może użyć kuponu ponownie. Endpoint woła system zamówień, nie klient, więc kraj nie jest
sprawdzany. Na produkcji powinien być dostępny tylko wewnętrznie (sieć/uwierzytelnianie serwis–serwis).

### Błędy

Każda odmowa to `application/problem+json` (RFC 9457) ze stabilnym polem `errorCode`, na którym klient może
oprzeć logikę. Przykład:
```json
{"type":"about:blank","title":"Conflict","status":409,"detail":"Coupon usage limit has been reached",
 "instance":"/api/v1/coupons/wiosna/redemptions","errorCode":"COUPON_EXHAUSTED"}
```

| Sytuacja | HTTP | `errorCode` |
|---|---|---|
| Niepoprawne dane wejściowe (format kodu, kraj, `maxUses`, `userId`, zepsuty JSON) | 400 | `VALIDATION_FAILED` (+ `errors[]` dla pól) |
| Kraj klienta inny niż kraj kuponu albo nieustalony | 403 | `COUNTRY_NOT_ALLOWED` |
| Kupon nie istnieje | 404 | `COUPON_NOT_FOUND` |
| Kod już istnieje (tworzenie) | 409 | `COUPON_ALREADY_EXISTS` |
| Użytkownik już użył kuponu | 409 | `COUPON_ALREADY_REDEEMED` |
| Limit użyć wyczerpany | 409 | `COUPON_EXHAUSTED` |
| Brak aktywnego użycia do wycofania (także ponowne wycofanie) | 404 | `REDEMPTION_NOT_FOUND` |
| Usługa geolokalizacji niedostępna | 503 + `Retry-After: 5` | `GEOLOCATION_UNAVAILABLE` |
| Nieoczekiwany błąd (szczegóły tylko w logu) | 500 | `INTERNAL_ERROR` |

Walidacja: kod `^[A-Z0-9_-]{3,32}$` po normalizacji (`strip()` + `toUpperCase(Locale.ROOT)`), `maxUses`
1…1 000 000, `userId` niepusty i maksymalnie 64 znaki, kraj z listy `Locale.getISOCountries()`.

## 4. Założenia

- **Brak daty ważności kuponu.** Zadanie jej nie wymaga (pomysł w sekcji [Co dalej](#9-co-dalej)).
- **`userId` pochodzi z requestu**, bo tak mówi zadanie. Produkcyjnie brałbym go z tokenu (JWT), żeby klient
  nie mógł podszyć się pod innego użytkownika.
- **Tworzenie kuponów jest bez uwierzytelniania**, zgodnie z treścią zadania. Produkcyjnie to endpoint
  administracyjny (osobna rola, najlepiej osobna usługa albo sieć).
- **Kraj nieustalony = odmowa (403).** Adres prywatny, zarezerwowany albo kraj spoza ISO 3166-1 (np. `XK`)
  nie uprawnia do rabatu.
- **Awaria geolokalizacji = odmowa (503), a nie przepuszczenie.** Rabat to pieniądze, więc wybrałem fail-closed.
- **Jeden użytkownik = jedno użycie kuponu.** Wymaganie było opcjonalne, ale zrealizowałem je.
- **Ponowna próba użytkownika, który już użył wyczerpanego kuponu**, zwraca `COUPON_ALREADY_REDEEMED`,
  a nie `COUPON_EXHAUSTED`. To dla niego bardziej informatywne.
- **Wycofanie użycia woła system zamówień**, nie klient, więc kraj nie jest sprawdzany. Endpoint powinien być
  dostępny tylko wewnętrznie. Produkcyjnie powiązałbym użycie z `orderId`, żeby klient nie mógł sam
  wycofywać i ponawiać użycia (ani wycofać cudzego).
- **`userId` jest przycinany** (`strip()`), więc `" user-1"` i `"user-1"` to ten sam użytkownik (BR-4).

## 5. Decyzje projektowe

**Architektura:** jeden moduł, package-by-feature (`coupon`) z lekkim podziałem heksagonalnym.

```
coupon/
  domain/          value objecty (CouponCode, CountryCode), model Coupon, porty (CouponRepository,
                   CountryResolver), wyjątki domenowe – bez zależności od Springa i JPA
  application/     CouponService – przypadki użycia: create, get, redeem, reverseRedemption
  infrastructure/
    persistence/   JPA + natywne zapytania atomowe, schemat w Flyway
    geo/           adapter ip-api.com, cache Caffeine, konfiguracja
    web/           kontroler REST, DTO, mapowanie wyjątków na ProblemDetail
```

| # | Decyzja | Odrzucone alternatywy | Uzasadnienie |
|---|---|---|---|
| 1 | Limit użyć pilnowany jednym warunkowym `UPDATE … WHERE current_uses < max_uses` | odczyt i sprawdzenie w Javie, `synchronized`, `@Version`, `SELECT … FOR UPDATE`, `SERIALIZABLE` | Atomowe w bazie, działa przy N instancjach, bez ponawiania prób (szczegóły w sekcji 6). |
| 2 | W transakcji najpierw `INSERT … ON CONFLICT DO NOTHING` (użytkownik), potem `UPDATE` (kupon) | odwrotna kolejność | Blokada „gorącego” wiersza kuponu jest brana jako ostatnia, więc trzymana najkrócej. Stała kolejność blokad wyklucza deadlocki. `ON CONFLICT` nie psuje transakcji, w przeciwieństwie do łapania wyjątku. |
| 3 | Wyczerpany limit to wynik (`RedemptionOutcome`) + `setRollbackOnly()` | wyjątek jako sterowanie przepływem | To normalna ścieżka biznesowa, a nie błąd. Rollback cofa wstawiony wiersz użycia. |
| 4 | Niezmienniki także w schemacie (`UNIQUE`, `CHECK`) | tylko logika w Javie | Defense in depth: nawet błąd w kodzie albo ręczny SQL nie przekroczy limitu. |
| 5 | Unikalność kodu wymusza indeks unikalny; aplikacja tłumaczy tylko naruszenie `uq_coupon_code` | „sprawdź, czy istnieje, potem zapisz” | Check-then-act to ten sam wyścig co przy limicie użyć. |
| 6 | Kod przechowywany znormalizowany, `toUpperCase(Locale.ROOT)` | `toUpperCase()` bez locale, `citext`, indeks na `UPPER(code)` | Turecki locale zamienia `i` → `İ`. Jedna kanoniczna postać upraszcza równość w Javie i zwykły `UNIQUE`. `citext` też by zadziałał, ale wymaga rozszerzenia i ukrywa regułę w bazie. |
| 7 | Geolokalizacja jako port `CountryResolver` + adapter HTTP, cache Caffeine, timeouty 500 ms / 1 s, fail-closed | wołanie API wprost w serwisie, fail-open | Adapter da się podmienić bez zmian w domenie (np. na offline'ową bazę MaxMind GeoLite2). Cache odciąża API z limitami. Błędy nie są cache'owane. |
| 8 | Geolokalizacja **przed** transakcją, `CouponService.redeem` bez `@Transactional` | wywołanie w transakcji | Nie trzymamy połączenia z puli ani blokad w czasie I/O sieciowego. Do tego `spring.jpa.open-in-view=false`. |
| 9 | IP klienta z `request.getRemoteAddr()` | ręczne czytanie `X-Forwarded-For` | Ręcznie czytany nagłówek to trywialne podszycie się pod inny kraj. Za load balancerem: `server.forward-headers-strategy=native`, wtedy Tomcat ufa nagłówkom tylko od zaufanych proxy. |
| 10 | Wątki wirtualne + ograniczona pula Hikari | duża pula wątków platformowych | Blokujące I/O (baza, geolokalizacja) jest tanie. Pula połączeń działa jak naturalny backpressure. |
| 11 | Testcontainers z tym samym obrazem PostgreSQL co w `compose.yaml` | H2 | Testy sprawdzają semantykę blokad i `ON CONFLICT` prawdziwego PostgreSQL. |
| 12 | `Clock` wstrzykiwany jako bean | `Instant.now()` w kodzie | Czas jest kontrolowany w testach. |
| 13 | Wycofanie użycia jako miękkie usunięcie (`reversed_at`) + częściowy indeks unikalny | `DELETE` wiersza | Zostaje ślad audytowy (kto, kiedy użył i wycofał). Częściowy indeks pozwala na ponowne użycie po wycofaniu. |
| 14 | Wycofanie nieistniejącego użycia to 404 `REDEMPTION_NOT_FOUND` | zawsze 204 (idempotentnie) | Wywołujący dostaje jasną informację, że nic się nie zmieniło (np. literówka w `userId`). Stan i tak jest idempotentny: licznik maleje najwyżej raz. |

## 6. Współbieżność – jak działa i jak to przetestowałem

Użycie kuponu to jedna krótka transakcja z dwoma zapytaniami (`CouponJpaRepository`):

```sql
-- 1. BR-4: wiersz użycia; konflikt = ten użytkownik już użył kuponu
INSERT INTO coupon_redemption (coupon_id, user_id, redeemed_at) VALUES (?, ?, ?)
ON CONFLICT (coupon_id, user_id) WHERE reversed_at IS NULL DO NOTHING;

-- 2. BR-2: sprawdzenie i inkrementacja w jednym zdaniu
UPDATE coupon SET current_uses = current_uses + 1
WHERE id = ? AND current_uses < max_uses;
```

- `INSERT` zwraca 0 wierszy → `ALREADY_REDEEMED`.
- `UPDATE` zwraca 0 wierszy → limit wyczerpany: rollback (wiersz z kroku 1 znika) i `EXHAUSTED`.
- W przeciwnym razie → `REDEEMED`.

**Dlaczego limit nie zostanie przekroczony?** Gdy 200 transakcji naraz chce zaktualizować ten sam wiersz,
PostgreSQL wpuszcza je po kolei (blokada wiersza). W READ COMMITTED transakcja, która czekała na blokadę,
**ponownie sprawdza warunek `WHERE` na najnowszej wersji wiersza**. Jeśli poprzednik właśnie zajął ostatnie
użycie, warunek `current_uses < max_uses` przestaje być spełniony i `UPDATE` nie zmienia nic. Nie ma tu
„lost update”, nie trzeba ponawiać prób i nie ma stanu w JVM, więc działa to tak samo przy jednej instancji,
jak i przy dziesięciu. `CHECK` w schemacie to ostatnia linia obrony.

**Jak to przetestowałem** (`ConcurrentRedemptionIT`, prawdziwy PostgreSQL w Testcontainers):

- 200 wątków wirtualnych startuje jednocześnie (`CountDownLatch`) na kupon z limitem 10. Wynik:
  dokładnie 10 × `REDEEMED`, 190 × `EXHAUSTED`, `current_uses = 10`, 10 wierszy użycia.
- 50 równoległych prób tego samego użytkownika daje dokładnie 1 × `REDEEMED` i 49 × `ALREADY_REDEEMED`.
- **Kontrola negatywna:** lokalnie podmieniłem implementację na naiwną („odczytaj licznik → sprawdź w Javie
  → zapisz”). Ten sam test przepuścił **183 użycia przy limicie 10**. To dowód, że test faktycznie wykrywa
  wyścig, a nie przechodzi przypadkiem. Naiwnej wersji nie ma w repozytorium.

**Wycofanie użycia (BR-6)** to też jedna krótka transakcja, z tą samą kolejnością blokad co użycie
(najpierw wiersz użytkownika, potem „gorący” wiersz kuponu), więc nie ma deadlocków:

```sql
UPDATE coupon_redemption SET reversed_at = GREATEST(?, redeemed_at)  -- odporne na rozjazd zegarów instancji
WHERE coupon_id = ? AND user_id = ? AND reversed_at IS NULL;   -- 0 wierszy → REDEMPTION_NOT_FOUND

UPDATE coupon SET current_uses = current_uses - 1
WHERE id = ? AND current_uses > 0;
```

Dwa równoległe wycofania tego samego użycia czekają na blokadę wiersza. Drugie ponownie sprawdza
`reversed_at IS NULL`, nic nie zmienia i licznik maleje dokładnie raz. Testy: 50 równoległych wycofań tego
samego użycia daje 1 sukces, a 10 wycofań ścigających się ze 100 nowymi użyciami nigdy nie przekracza limitu
(`current_uses` = liczba aktywnych użyć).

Dodatkowo `CouponApiIT.geolocationIsCalledOutsideTransaction` sprawdza, że w chwili wywołania geolokalizacji
nie jest otwarta żadna transakcja.

## 7. Skalowanie na Black Friday

Aplikacja jest bezstanowa (cały stan współdzielony jest w PostgreSQL), więc skaluje się horyzontalnie:
więcej instancji za load balancerem. Wąskie gardła i dalsze kroki, od najprostszych:

1. **Gorący wiersz.** Popularny kupon to jeden wiersz, a każde użycie bierze na nim krótką blokadę.
   Transakcja jest krótka (dwa zapytania, bez I/O sieciowego w środku), więc wystarcza to do sporego ruchu.
   Gdy przestanie:
   - **Redis + skrypt Lua** jako atomowy licznik przed bazą, z asynchronicznym zapisem użyć;
   - **liczniki shardowane:** limit podzielony na N wierszy, losowy wybór shardu;
   - **pula tokenów:** wiersz na każde użycie i `SELECT … FOR UPDATE SKIP LOCKED`, wtedy nie ma kolejki
     na jednym wierszu.
2. **Połączenia z bazą.** Każda instancja ma ograniczoną pulę Hikari. Przy wielu instancjach dodałbym
   PgBouncer (transaction pooling), żeby nie wyczerpać `max_connections`.
3. **Geolokalizacja.** Zewnętrzne API ma limity i dokłada opóźnienia (darmowe ip-api.com to 45 zapytań na
   minutę, tylko HTTP, bez użytku komercyjnego). Produkcyjnie wybrałbym **offline'ową bazę MaxMind GeoLite2
   albo DB-IP** w pamięci: zero opóźnień, brak limitów, a IP nie opuszcza infrastruktury (RODO). Dzięki portowi
   `CountryResolver` to wymiana jednego adaptera. Do tego circuit breaker (Resilience4j), żeby przy awarii
   providera szybko odmawiać, zamiast czekać na timeout.
4. **Dalsze przetwarzanie** (powiadomienia, rozliczenia) przez outbox + Kafka, poza ścieżką żądania.

## 8. Bezpieczeństwo

- **Spoofing kraju przez `X-Forwarded-For`:** nagłówek jest ignorowany, liczy się adres połączenia
  (test `ignoresForwardedHeaderWithoutTrustedProxy`).
- **Fail-closed:** brak pewności co do kraju oznacza brak rabatu.
- **Enumeracja kodów:** kody da się zgadywać, odpytując API. Produkcyjnie: rate limiting na gatewayu albo
  w aplikacji (Bucket4j), długie losowe kody dla kuponów jednorazowych, monitoring 404.
- **Brak uwierzytelniania** (zgodnie z zadaniem). Produkcyjnie: tworzenie tylko dla roli administracyjnej,
  `userId` z tokenu.
- **Odpowiedzi błędów nie zdradzają szczegółów:** 500 nie zawiera stack trace, SQL ani nazw hostów,
  komunikaty nie odbijają danych wejściowych, IP klienta nie trafia do komunikatu dla klienta.
- **Walidacja wejścia** na brzegu (Bean Validation) i w domenie (value objecty) oraz ograniczenia w schemacie.
- **RODO:** adres IP to dana osobowa. Zewnętrzne API do geolokalizacji to przekazanie danych podmiotowi
  trzeciemu, co jest kolejnym argumentem za bazą offline.
- **Kontener** działa jako użytkownik bez uprawnień roota, a kontekst budowania obrazu jest ograniczony allow-listą.

## 9. Co dalej

- Data ważności kuponu (`valid_from` / `valid_to`) i dezaktywacja kuponu.
- Powiązanie użycia z `orderId` i uwierzytelnianie serwis–serwis dla wycofania.
- Klucz idempotencji dla `POST /redemptions` (bezpieczne ponowienia po timeoucie po stronie klienta).
- Metryki Micrometer (liczba użyć, odmowy według `errorCode`, czasy geolokalizacji) i alerty.
- Circuit breaker na geolokalizacji, offline'owa baza GeoIP.
- Testy obciążeniowe (Gatling/k6) na scenariuszu „jeden gorący kupon”.

## 10. Praca z AI

Projekt powstawał z pomocą **Claude Code**. Kontekst dla agenta (domena, reguły biznesowe, decyzje,
których nie wolno zmieniać, zasady pracy) jest celowo zakomitowany w [`AGENTS.md`](AGENTS.md).

**Podział ról.** Ja projektowałem rozwiązanie i podejmowałem decyzje: wybór mechanizmu współbieżności,
fail-closed, zakres. Agent pomagał w implementacji, testach i przeglądzie. AI było używane w całym
projekcie. Trailer `Co-Authored-By` dodawałem w części commitów według własnego uznania, więc jego brak
nie oznacza, że przy danym commicie agent nie pomagał.

**Jak weryfikowałem wynik agenta:**
- **TDD:** najpierw test, który nie przechodzi, potem implementacja. Dzięki temu test na adres IPv6 wyłapał
  zamianę `:` na `%3A` w URL do providera.
- **Kontrola negatywna testu współbieżności** z naiwną implementacją (sekcja 6).
- **Przegląd każdego diffu** przed commitem: agent nie commituje sam.
- **Fakty sprawdzalne narzędziem** (daty, wersje bibliotek, API Spring Boot 4) weryfikowałem, zamiast
  przyjmować je „z pamięci” modelu.

**Przykłady, w których poprawiłem albo odrzuciłem propozycję agenta:**
- Agent zasugerował **Spocka** w testach jako ukłon w stronę stacku firmy. Zostałem przy JUnit 5 + AssertJ:
  Spocka nie znam, a każdą linijkę chcę umieć obronić.
- Agent wydzielił **osobną klasę konfiguracyjną** tylko po to, żeby wystawić bean `Clock`. Uznałem to za
  przerost formy, bean jest teraz jedną metodą w klasie aplikacji.
- Agent przygotował harmonogram prac z **błędnymi dniami tygodnia**. Wyłapałem to, a od tamtej pory daty
  i wersje sprawdzamy narzędziem.
- Z szerokiego przeglądu tematów, który zaproponował agent, **zawęziłem priorytety** do współbieżności,
  skalowania i bezpieczeństwa w kontekście Black Friday. Atomową operację w bazie zaakceptowałem pod
  warunkiem, że agent pokaże, jak ją obronić (ponowna ewaluacja `WHERE`, kontrola negatywna testu).
