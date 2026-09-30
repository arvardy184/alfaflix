> **Historical document.** This describes the earlier OMDb-based version of Alfaflix. The app now uses TMDB; see [README.md](README.md) for the current design. Kept for history only.

# Alfaflix — Technical Design Specification

**Date:** 2026-09-09
**Status:** Approved, pending implementation
**Author:** Engineering

---

## 1. Overview

Alfaflix is an Android movie discovery client for the [OMDb API](https://www.omdbapi.com/).
Users browse curated collections, search the OMDb catalogue live as they type, inspect
rich movie detail, and save movies to a favorites list that persists locally and works
without a network connection.

The application targets a technical review, so the guiding constraint is not feature
count. It is that every abstraction present in the repository should be defensible to a
senior reviewer, and every abstraction absent from it should be defensible too. Section 16
(Deliberate Non-Features) exists for the second half of that sentence.

**Tagline:** Discover. Save. Come back anytime.

---

## 2. The central constraint: what OMDb actually is

OMDb is a title lookup API. It offers exactly two useful operations:

| Operation | Request | Returns |
| --- | --- | --- |
| Search | `?s=<query>&page=<n>&type=<t>` | Up to 10 summary results per page, plus `totalResults` |
| Lookup | `?i=<imdbID>&plot=<short\|full>` | One full detail record |

It has **no** trending endpoint, no popularity ranking, no personalised recommendations,
no user reviews, and no social graph. Every design decision below flows from accepting
that honestly rather than simulating capabilities the API does not have.

Two API behaviours drive real code:

1. **Failures arrive as HTTP 200.** A miss returns `{"Response":"False","Error":"Movie not found!"}`
   with a success status code. HTTP status alone is therefore never sufficient to decide
   whether a request succeeded.
2. **`"N/A"` is used as the null literal** across nearly every field, including `Poster`.

---

## 3. Decision log

| # | Decision | Rationale |
| --- | --- | --- |
| D1 | Single Gradle module | Four screens do not justify multi-module build complexity. Layering is enforced by package boundaries and dependency direction instead. See §16. |
| D2 | Tiered caching, not uniform SSOT | Cache where it buys offline value; skip it where it does not. See §7. |
| D3 | Search results deliberately uncached | Live search wants fresh data, and arbitrary typed queries have a near-zero cache hit rate. See §16. |
| D4 | Home shows 3 curated collections | Visual focus over volume. Labelled "Featured Collections", never "Trending" or "Top Rated" — OMDb provides no ranking data, so those labels would be fabricated. |
| D5 | IMDb ID is the only movie identity | Stable, unique, and what OMDb's lookup endpoint consumes. Titles are neither unique nor stable. |
| D6 | Navigation passes `imdbId` only | Detail reconstructs itself from the ID, so the destination is deep-linkable and process-death safe. |
| D7 | Favorite state derived from one Room flow | Makes cross-screen desync structurally impossible rather than manually maintained. See §10. |
| D8 | Long detail TTL (7 days) | Cast, plot and runtime are effectively immutable. OMDb's free tier allows 1000 requests/day. See §8. |
| D9 | `Cached<T>` wrapper carries staleness to the UI | Lets the UI say "Showing saved data · updated 12 minutes ago" without the ViewModel re-deriving it. |
| D10 | Robolectric for JVM-side Room and Compose tests | Instrumented tests will not be executed in this environment, so the critical behaviour is pulled onto the JVM where `./gradlew test` genuinely runs it. |
| D11 | Release APK falls back to debug signing | Guarantees the deliverable APK is installable by a reviewer without shipping a keystore in the repository. See §15.3. |

---

## 4. Architecture

Single module, three layers, strictly one-directional dependencies.

```
┌─────────────────────────────────────────────┐
│ presentation   Compose UI + ViewModels      │
│                UI state, navigation         │
└───────────────────────┬─────────────────────┘
                        │ depends on
┌───────────────────────▼─────────────────────┐
│ domain         Models, repository contracts │
│                Use cases                    │
│                (pure Kotlin, no Android)    │
└───────────────────────▲─────────────────────┘
                        │ implements
┌───────────────────────┴─────────────────────┐
│ data           Retrofit + DTOs + mappers    │
│                Room + entities + DAOs       │
│                Repository implementations   │
└─────────────────────────────────────────────┘
```

**Rules enforced during review:**

- `domain` is pure Kotlin. No Android imports, no Retrofit, no Room.
- ViewModels depend on use cases only. They never see a `Dao`, a Retrofit service, or a DTO.
- Repository *interfaces* live in `domain`; *implementations* live in `data`. Dependency
  inversion is what makes the domain layer testable without a fake Android environment.
- DTOs never escape `data`. Mapping to domain models happens at the data-source boundary.

### 4.1 Rule on use cases

**Use cases where there is real logic; domain repository interfaces for plain observable
reads.**

An earlier draft of this spec required every ViewModel to read through a use case. That
rule was revised during implementation because it forced pass-through files —
`GetSurpriseMovieUseCase` forwarding to `repository.randomCachedMovie()` adds a file and no
behaviour, which is exactly the padding the brief warns against.

Repository *interfaces* live in `domain`, so a ViewModel depending on one is already
depending on the domain layer; nothing about Clean Architecture is violated. The rule that
actually matters is unchanged and absolute: **ViewModels never touch a Room DAO, a Retrofit
service, or a DTO.**

The result is four use cases, each doing genuine composition or branching (§11).

---

## 5. Package structure

```
com.application.alfaflix
├── AlfaflixApplication.kt        @HiltAndroidApp
├── MainActivity.kt
├── core/
│   ├── designsystem/              Theme, color, type, spacing tokens
│   │   └── component/             Reusable: PosterImage, MovieCard, Shimmer,
│   │                              EmptyState, ErrorState, StaleDataBanner
│   ├── network/                   NetworkMonitor, connectivity Flow
│   ├── result/                    AppResult, AppError, Cached, RefreshState
│   └── time/                      TimeProvider (injectable clock, testable TTL)
├── data/
│   ├── remote/
│   │   ├── api/                   OmdbApi (Retrofit interface)
│   │   ├── dto/                   SearchResponseDto, MovieDetailDto, ...
│   │   ├── mapper/                DTO → domain
│   │   └── MovieRemoteDataSource.kt
│   ├── local/
│   │   ├── database/              AlfaflixDatabase, TypeConverters
│   │   ├── dao/                   FavoriteDao, RecentlyViewedDao,
│   │   │                          MovieDetailDao, CollectionDao
│   │   ├── entity/                Room entities
│   │   └── mapper/                entity ↔ domain
│   ├── collection/                CuratedCollections (static catalogue)
│   └── repository/                MovieRepositoryImpl, FavoritesRepositoryImpl,
│                                  RecentlyViewedRepositoryImpl
├── domain/
│   ├── model/                     Movie, MovieDetail, MovieType, Rating,
│   │                              SearchPage, CuratedCollection
│   ├── repository/                Repository interfaces
│   └── usecase/                   See §11
├── presentation/
│   ├── navigation/                Routes, AlfaflixNavHost, bottom bar
│   ├── home/
│   ├── search/
│   ├── detail/
│   └── favorites/
└── di/                            Hilt modules
```

There is no `util` package. Helpers live beside the code that needs them.

---

## 6. Data model

### 6.1 Domain models

```kotlin
enum class MovieType { MOVIE, SERIES, EPISODE, GAME, UNKNOWN }

data class Movie(
    val imdbId: String,
    val title: String,
    val year: String,          // OMDb emits ranges like "2011–2019"; keep as text
    val type: MovieType,
    val posterUrl: String?     // null when OMDb returned "N/A"
)

data class MovieDetail(
    val imdbId: String,
    val title: String,
    val year: String,
    val type: MovieType,
    val posterUrl: String?,
    val rated: String?,
    val released: String?,
    val runtimeMinutes: Int?,      // parsed from "142 min"
    val genres: List<String>,      // split from "Action, Crime, Drama"
    val director: String?,
    val writers: List<String>,
    val cast: List<String>,
    val plot: String?,
    val languages: List<String>,
    val countries: List<String>,
    val awards: String?,
    val imdbRating: Double?,       // parsed from "9.0"
    val imdbVotes: Int?,           // parsed from "2,845,132"
    val metascore: Int?,
    val ratings: List<Rating>,
    val boxOffice: String?
)

data class Rating(val source: String, val value: String)

data class SearchPage(
    val movies: List<Movie>,
    val totalResults: Int,
    val page: Int
)
```

### 6.2 The mapper contract

Mappers are the highest-value unit-test target in the project. They must:

- Convert every `"N/A"` to `null` (or an empty list), so the UI omits fields naturally
  instead of rendering "N/A" everywhere. §4 of the brief is satisfied by this rule alone.
- Parse `"142 min"` → `142`, `"2,845,132"` → `2845132`, `"9.0"` → `9.0`.
- Split comma-delimited strings into lists, trimming whitespace and dropping blanks.
- Never throw on malformed input. A missing or unparseable field becomes `null`.

### 6.3 Room entities

| Entity | Primary key | Notes |
| --- | --- | --- |
| `FavoriteMovieEntity` | `imdbId` | Summary fields + `addedAt`. Ordered by `addedAt DESC`. |
| `RecentlyViewedEntity` | `imdbId` | Summary fields + `viewedAt`. PK on `imdbId` gives deduplication for free via `OnConflictStrategy.REPLACE`; re-viewing updates the timestamp and moves the row to the top. Trimmed to 20 rows. |
| `MovieDetailEntity` | `imdbId` | Full detail + `cachedAt`. List fields via `TypeConverter`. |
| `CollectionMovieEntity` | `(collectionId, imdbId)` | Summary fields + `position` for stable ordering. Indexed on `collectionId`. |
| `CollectionRefreshEntity` | `collectionId` | `refreshedAt`. Separate from the rows so a collection that legitimately returns zero results still records a successful refresh. |

Entities deliberately store only the fields the UI needs, not the whole API response.

---

## 7. Caching strategy (tiered)

| Data | Strategy | TTL | Rationale |
| --- | --- | --- | --- |
| Favorites | Room is the single source of truth. Never fetched. | — | User-owned data. Network is irrelevant to it. |
| Recently Viewed | Room is the single source of truth. | — | User-owned data. |
| Movie detail | Cache-first, then conditional refresh. Emit cache immediately; refresh only if stale or forced. On refresh failure, keep the cache and surface a non-blocking error. | 7 days | This is where offline hurts most: opening a saved movie without a connection. Detail is near-immutable, so a long TTL is correct *and* minimises requests. |
| Home collections | Cache-first, then conditional refresh. Same failure semantics. | 24 hours | Makes a cold offline launch useful instead of empty. |
| Search results | **Not cached.** | — | See §16. |

### 7.1 The `Cached<T>` wrapper

Both cached reads emit the same shape, so the UI treats staleness uniformly:

```kotlin
sealed interface RefreshState {
    data object Idle : RefreshState
    data object InProgress : RefreshState
    data class Failed(val error: AppError) : RefreshState
}

data class Cached<T>(
    val data: T?,                 // null only when nothing has ever been cached
    val lastUpdatedAt: Long?,     // epoch millis, drives "updated 12 minutes ago"
    val refresh: RefreshState
)
```

The presentation layer derives one small model from it, so no screen re-implements the
"is this stale, and how do I phrase it" logic:

```kotlin
data class Staleness(
    val lastUpdatedAt: Long,
    val reason: Reason              // Offline | RefreshFailed
) { enum class Reason { OFFLINE, REFRESH_FAILED } }
```

`Staleness` is non-null only when cached data is on screen *and* the latest refresh did
not succeed. Fresh content carries `null` and shows no banner at all — the messaging is
subtle by construction rather than by styling.

This single type is what lets the UI distinguish the four states the brief calls out:

| Condition | UI response |
| --- | --- |
| `data == null`, `refresh == InProgress` | Skeleton loading |
| `data == null`, `refresh == Failed` | Full-screen error with retry |
| `data != null`, `refresh == Failed` | **Content stays.** Subtle banner: "Showing saved data" + retry |
| `data != null`, offline | Content stays. Banner: "You're offline · updated 12 minutes ago" |

Content is never replaced by an error screen when usable data exists.

---

## 8. Request minimisation

OMDb's free tier permits 1000 requests/day, which a naive live-search implementation
exhausts in minutes. Countermeasures, in order of impact:

1. **Debounce 400 ms + `distinctUntilChanged`** on the search query. Typing
   `b→ba→bat→batm→batma→batman` produces **one** request, not six.
2. **Minimum query length of 2.** Single characters return noise and burn quota.
3. **TTL-gated refresh.** A fresh cache short-circuits before any network call is made —
   re-opening the same movie within 7 days costs zero requests.
4. **OkHttp HTTP cache** (10 MB) as a second line of defence against duplicate in-flight
   and repeat requests.
5. **Refresh is never triggered from recomposition.** Only cold start, TTL expiry, or an
   explicit pull-to-refresh initiates network work.

Pull-to-refresh bypasses the TTL (`force = true`), so a reviewer can always demonstrate a
live refresh regardless of cache age.

---

## 9. Error handling

```kotlin
sealed interface AppError {
    data object Offline : AppError            // no connectivity
    data object Timeout : AppError
    data class Server(val code: Int) : AppError
    data class Api(val message: String) : AppError   // OMDb Response=False
    data object Unknown : AppError
}

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
```

**Mapping happens at the UI layer**, via a `@Composable AppError.asMessage(): String`
backed by `stringResource`. No exception text, HTTP code, or stack trace ever reaches a
user, and every message stays translatable.

### 9.1 OMDb's HTTP-200 failures

The remote data source inspects `Response` before anything else:

| OMDb `Error` string | Mapped to | UI |
| --- | --- | --- |
| `"Movie not found!"` | Empty result, **not** an error | "No movies found" empty state |
| `"Too many results."` | Empty result with a hint | "Too many matches — try a more specific title" |
| `"Invalid API key!"` | `AppError.Api` | Generic failure message + retry |
| anything else | `AppError.Api` | Generic failure message + retry |

Treating "not found" as an error state would be a correctness bug: an empty search result
is a successful request.

### 9.2 Connectivity

`NetworkMonitor` wraps `ConnectivityManager` with `NET_CAPABILITY_VALIDATED` and exposes
`Flow<Boolean>`. It is injected, so tests substitute a fake. It exists to distinguish
"offline" from "server error" — it is not a decorative flag.

---

## 10. Favorite consistency

One Room flow is the single source of truth for favorite state across the entire app:

```kotlin
fun observeFavoriteIds(): Flow<Set<String>>
```

Every screen that renders a movie combines its own stream with that one:

```kotlin
combine(movies, favoriteIds) { list, ids ->
    list.map { MovieListItem(movie = it, isFavorite = it.imdbId in ids) }
}
```

Consequences:

- No screen owns favorite state, so no screen can drift out of sync.
- Toggling a favorite on Detail updates Home, Search and Favorites in the same frame.
- The desync failure mode named in the brief (§7) is structurally impossible, not merely
  avoided by discipline.

Toggle writes go through `ToggleFavoriteUseCase`, which reads current state and branches
to add or remove. The write lands in Room, and every observer re-emits.

---

## 11. Use cases

| Use case | Real work it does |
| --- | --- |
| `SearchMoviesUseCase` | Owns search policy: trims the query, rejects anything under 2 characters before it reaches the network, restricts results to films. |
| `ObserveMovieDetailUseCase` | Joins the cached-detail stream with favorite state into one UI-ready stream. |
| `ObserveHomeFeedUseCase` | Combines 3 collection streams + recently viewed + favorite IDs into a single `HomeFeed`, including refresh aggregation and oldest-timestamp selection. |
| `ToggleFavoriteUseCase` | Reads current state and branches to add or remove, returning the resulting state. |

No use case is a bare pass-through. Three that an earlier draft proposed were deliberately
**not** built, because each would have forwarded a single call unchanged:

- `RecordMovieViewUseCase` — dedup and trimming belong to `RecentlyViewedRepository`, which
  already owns them; the ViewModel calls the repository.
- `ObserveFavoritesUseCase` — `FavoritesViewModel` observes `FavoritesRepository` directly.
- `GetSurpriseMovieUseCase` — `HomeViewModel` calls `randomCachedMovie()` directly.

---

## 12. Presentation

### 12.1 Navigation

Jetpack Navigation Compose with **type-safe routes** (`@Serializable` route classes,
`SavedStateHandle.toRoute<T>()`).

```
NavHost
├── HomeRoute        ─┐
├── SearchRoute       ├─ bottom navigation bar
├── FavoritesRoute   ─┘
└── MovieDetailRoute(imdbId: String)   full screen, no bottom bar
```

Detail receives a `String` and nothing else. No serialized movie objects cross a
navigation boundary.

### 12.2 UI state

```kotlin
sealed interface HomeUiState {
    data object Loading : HomeUiState                    // cold start, no cache
    data class Error(val error: AppError) : HomeUiState   // no cache AND refresh failed
    data class Content(
        val recentlyViewed: List<MovieListItem>,
        val collections: List<CollectionRow>,
        val isRefreshing: Boolean,
        val staleness: Staleness?,     // drives the subtle banner
        val isOffline: Boolean
    ) : HomeUiState
}

data class SearchUiState(
    val query: String,
    val stage: Stage,                  // Idle | Loading | Content | Empty | Error
    val movies: List<MovieListItem>,
    val totalResults: Int,
    val canLoadMore: Boolean,
    val isLoadingNextPage: Boolean,
    val nextPageError: AppError?,      // inline footer error, never replaces the list
    val isOffline: Boolean
)

sealed interface MovieDetailUiState {
    data object Loading
    data class Error(val error: AppError)
    data class Content(
        val detail: MovieDetail,
        val isFavorite: Boolean,
        val staleness: Staleness?,
        val isRefreshing: Boolean
    )
}
```

State is immutable, exposed as `StateFlow`, and collected with
`collectAsStateWithLifecycle()`.

### 12.3 Screens

**Home** — Featured Collections. Recently Viewed row first (omitted when empty), then
three curated collection carousels. Pull-to-refresh. "Surprise Me" action in the top bar.

Curated collections are static data in `data/collection/CuratedCollections.kt`:

| Row label | Seed query | Type filter |
| --- | --- | --- |
| Caped Crusaders | `batman` | movie |
| A Galaxy Far, Far Away | `star wars` | movie |
| Return to Middle-earth | `lord of the rings` | movie |

The section header reads **"Featured Collections"**. No row is labelled Trending, Popular
or Top Rated, because OMDb supplies no ranking data and those labels would be fabricated.

**Search** — Search field with live results, poster grid, infinite scroll, per-state
empty/error handling. Offline state explicitly routes the user to Favorites and Recently
Viewed rather than showing a dead end.

**Detail** — Hero poster with scrim → title → year · rated · runtime → IMDb rating →
genre chips → plot → director/writers → cast → other ratings → awards/box office →
favorite action. Fields absent from the API are omitted entirely, not rendered as "N/A".

**Favorites** — Grid of saved movies, swipe or tap to unfavorite, meaningful empty state.
Fully functional offline.

### 12.4 Loading and empty states

Skeleton/shimmer placeholders that preserve layout geometry, so content arrival does not
shift the page. Pagination shows a small footer spinner; refresh shows an indicator while
existing content stays on screen. Empty states carry helpful copy:

| Context | Copy |
| --- | --- |
| Search, no query | "Find your next movie" |
| Search, no results | "No movies found" |
| Favorites | "Your favorite movies will appear here" |
| Recently Viewed | "Movies you explore will appear here" |

---

## 13. Search pipeline

```kotlin
queryFlow
    .debounce(400)
    .map(String::trim)
    .distinctUntilChanged()
    .flatMapLatest { q ->
        if (q.length < 2) flowOf(SearchStage.Idle)
        else searchMovies(query = q, page = 1)
    }
    .onEach { /* reset state to page 1 */ }
    .launchIn(viewModelScope)
```

Pagination is imperative and separate: `loadNextPage()` launches a job guarded by
`isLoadingNextPage` and `canLoadMore`, where `canLoadMore = accumulated.size < totalResults`.

### 13.1 Stale-append correctness (explicit requirement)

`flatMapLatest` cancels the previous **first-page** load, but an in-flight **next-page**
job is not part of that chain. Without intervention, page 3 of "batman" can append itself
onto results for "inception".

Two independent guards:

1. **Cancellation** — when the debounced query emits, the retained `nextPageJob` is
   explicitly cancelled before new state is installed.
2. **Query token validation** — every page load carries the query string it was issued
   for, and the append is dropped if that token no longer matches current state.

The second guard makes the invariant hold even if cancellation loses a race. This is
treated as a correctness requirement with a dedicated regression test (§14).

---

## 14. Testing strategy

All tests run on the JVM via `./gradlew test`. Room and Compose tests run under
Robolectric so they genuinely execute here rather than shipping as never-run code.
Fakes are preferred over mocks for readability; MockK is used only where a fake would be
more code than it is worth.

| Area | Coverage |
| --- | --- |
| **Mappers** | `"N/A"` → null across every field; runtime/votes/rating parsing; comma-list splitting; malformed input never throws. |
| **Room (Robolectric, in-memory)** | Favorites persist and re-emit; recently-viewed deduplicates by `imdbId`; re-viewing moves the row to the top; trim keeps exactly 20; collection rows preserve `position` ordering. |
| **Repository** | Remote success populates the cache; remote failure with a warm cache returns cached data plus `RefreshState.Failed`; remote failure with a cold cache returns `Failure`; TTL-fresh reads make no network call; `force = true` bypasses the TTL. |
| **Use cases** | Toggle favorite in both directions; record-view dedup and trim; home-feed composition; surprise-me on an empty pool returns null. |
| **SearchViewModel** | Initial idle state; debounce collapses six keystrokes into **one** call (Turbine + virtual time); empty result → Empty not Error; API error → Error; retry re-issues; pagination appends and sets `canLoadMore`; a second `loadNextPage()` while one is in flight does not duplicate the request; **query change never appends stale results** (§13.1 regression test). |
| **HomeViewModel** | Cold start loading; cache-present skips loading; refresh failure preserves content and sets staleness. |
| **DetailViewModel** | Loads by ID; records the view exactly once; favorite toggle reflects immediately. |
| **Compose (Robolectric)** | Critical journey: search → result appears → open detail → favorite → navigate to Favorites → movie is present. Plus: error state exposes a working retry; empty state renders correct copy. |

Instrumented tests under `androidTest/` are written and committed but **not executed in
this environment**. This is stated plainly in the README rather than implied to have passed.

---

## 15. Build and toolchain

### 15.1 Baseline

The repository arrived with an Android Studio scaffold whose build was **already broken**:
AGP 8.13.2 and Kotlin 2.0.21 paired with AndroidX libraries (`core-ktx` 1.19.0,
`lifecycle` 2.11.0, `activity-compose` 1.13.0) that require **AGP 9.1.0+ and compileSdk 37**.
Verifying the baseline before making changes is what surfaced this, and it justified the
"foundation phase must end green" rule below.

### 15.2 Resolved toolchain

Dependency versions were **resolved empirically against live repository metadata, not
guessed**, then proven by a green build before any feature code was written.

| Component | Version |
| --- | --- |
| Android Gradle Plugin | 9.4.0 |
| Gradle | 9.7.1 |
| Kotlin | 2.4.20 |
| KSP | 2.3.11 |
| Hilt | 2.60.1 |
| Room | 2.8.4 |
| Compose BOM | 2026.08.00 |
| Navigation Compose | 2.10.0 |
| Retrofit / OkHttp | 3.0.0 / 5.5.0 |
| Coil | 3.6.2 |
| Robolectric / Turbine | 4.16.1 / 1.2.1 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 25 |
| jvmTarget | 17 |

**Four non-obvious incompatibilities had to be resolved**, recorded here because none is
discoverable without hitting it:

1. AGP 9 rejects the `org.jetbrains.kotlin.android` plugin — Kotlin support is built in.
2. **KSP is incompatible with AGP's built-in Kotlin.** Hilt and Room both require KSP, so
   built-in Kotlin must be disabled (`android.builtInKotlin=false`) and KGP applied explicitly.
3. KGP then fails against AGP 9's new DSL (`ApplicationExtensionImpl cannot be cast to
   BaseExtension`), requiring `android.newDsl=false`.
4. KSP no longer versions as `<kotlin>-<ksp>` (e.g. `2.2.21-2.0.5`) but on an independent
   line (`2.3.11`). Missing this makes Kotlin appear capped at 2.2.21.

Separately, `platforms;android-37` does not exist: Android now publishes **minor-versioned
SDK packages**, so the correct install target is `platforms;android-37.0`.

Both `android.builtInKotlin=false` and `android.newDsl=false` are temporary compatibility
shims, commented as such in `gradle.properties`. They can be removed once KGP supports
AGP 9's new DSL and KSP supports built-in Kotlin.

Other Phase 2 changes:

- `jvmTarget` 11 → 17; the deprecated `kotlinOptions` block replaced with `compilerOptions`
- Added `INTERNET` and `ACCESS_NETWORK_STATE` permissions
- Enabled `buildConfig`; plumbed `OMDB_API_KEY` from `local.properties` → `BuildConfig`
- `testOptions { unitTests.isIncludeAndroidResources = true }` for Robolectric
- The package was **not** renamed: `com.application.alfaflix` is retained to stay
  consistent with the existing GitHub remote.

### 15.3 API key and signing

The key is read from `local.properties` (git-ignored) into `BuildConfig`. The build fails
with an actionable message if it is absent.

**Honest security note for the README:** `BuildConfig` keeps the key out of version
control. It does **not** make the key secret — it is a string constant in the APK and can
be recovered by anyone who unzips it. Genuine protection requires a backend proxy that
holds the key server-side. That is out of scope here and will be documented as such.

Release signing reads an optional keystore from `local.properties`. When none is
configured it falls back to the debug signing config, so `assembleRelease` always produces
an **installable** APK for a reviewer without committing a keystore to the repository.

---

## 16. Deliberate non-features

Documented in the README under this exact heading.

**Search results are not cached.** Caching paginated search would require a query→results
join table plus per-query page bookkeeping. The cache hit rate on arbitrary typed strings
is near zero, and live search actively *wants* fresh data, so the machinery would add
meaningful complexity for negligible benefit. Offline search instead shows an explicit
offline state that directs the user to Favorites and Recently Viewed, which do work
offline. This is a stated trade-off, not an oversight.

**The project is a single Gradle module.** Multi-module would demonstrate familiarity with
scaling patterns, but for four screens it buys build complexity and configuration surface
without buying isolation that package boundaries do not already provide. Layer separation
is enforced by dependency direction and reviewed as such. At the scale where parallel team
ownership or build-time became a real constraint, the existing layer boundaries are
already the natural module seams.

**No recommendations or personalisation.** OMDb exposes no ranking, popularity, or
similarity data. Any "recommended for you" row would be fabricated from a hardcoded query
and would misrepresent the product's capability. Curated collections are honestly labelled
as curated.

**No authentication or backend.** Nothing in the product requires user identity: favorites
and history are device-local by design, which is also what makes them work offline. Adding
auth would introduce a server, a session lifecycle, and a privacy surface in exchange for
no user-visible benefit. The one thing a backend *would* legitimately buy — real API key
secrecy (§15.3) — is documented rather than built, because the assignment scope is a
client application.

---

## 17. Risks

| Risk | Mitigation |
| --- | --- |
| Kotlin/KSP/Compose/AGP version mismatch | Resolved in Phase 2 by an empirical green build before feature work begins. |
| Robolectric + Compose test setup proves unstable | Fall back to plain JVM ViewModel/repository tests and move the journey test to `androidTest/`. Prioritise meaningful tests over the Robolectric mechanism itself. |
| R8 breaks the release APK at runtime, undetectable without a device | Ship correct keep rules; verify `assembleRelease` completes; state plainly that runtime smoke-testing of the minified APK remains outstanding. |
| OMDb daily quota exhausted during development | §8 minimisation applies during development too; cached responses reduce repeat calls. |
| Curated seed queries return incoherent results | Verified against the live API once the key is available; queries adjusted if a row looks like noise. |

---

## 18. Phase plan

| Phase | Deliverable | Exit criterion |
| --- | --- | --- |
| 1 | This specification | Committed |
| 2 | Foundation: rename, dependencies, DI, theme, build config | `assembleDebug` **and** `test` both green |
| 3 | Data layer: Retrofit, DTOs, mappers, Room, DAOs, repositories | Mapper + Room + repository tests pass |
| 4 | Domain layer: models, contracts, use cases | Use case tests pass |
| 5 | Presentation: Home, Search, Detail, Favorites, navigation | App navigable end to end |
| 6 | Advanced: debounce, pagination, offline, recently viewed, pull-to-refresh | §13.1 regression test passes |
| 7 | UX polish: skeletons, empty/error states, motion, theme, accessibility | Visual review |
| 8 | Testing: complete the matrix in §14 | `./gradlew test` green |
| 9 | Documentation: README, architecture, screenshots guide | README complete |
| 10 | Senior audit: fix findings, not merely report them | `assembleRelease` green, audit clean |

---

## 19. Acceptance criteria

Tracked against the brief's §38. The project is complete when: all core features work;
favorites survive an app restart; pagination, pull-to-refresh, offline behaviour and retry
all function; MVVM, repository pattern and DI are demonstrably present; DTOs are separated
from domain models; loading, empty and error states are handled on every screen;
`./gradlew test` passes; `assembleDebug` and `assembleRelease` both succeed; no hardcoded
secrets; no `GlobalScope`; no dead code or unresolved TODOs; and the README is complete
including §16.
