> **Historical document.** This describes the earlier OMDb-based version of Alfaflix. The app now uses TMDB; see [README.md](README.md) for the current design. Kept for history only.

# Alfaflix Implementation Plan

> **STATUS: COMPLETE.** All 18 tasks implemented and verified — 126 JVM tests passing,
> `assembleDebug` and `assembleRelease` both green. Two items are outstanding by
> necessity, not omission: README screenshots must be captured on a device, and the
> instrumented tests in `androidTest/` were written but never executed (no emulator was
> available). Both are stated plainly in the README.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.
>
> **Commits are owned by the repository author.** Every task ends with a **Checkpoint**, not a commit step. Do not run `git commit` or `git push`. A suggested commit message is provided at each checkpoint for the author to use.

**Goal:** Build Alfaflix, an offline-capable OMDb movie discovery app in a single Gradle module, demonstrating MVVM + Clean Architecture with genuinely runnable JVM tests.

**Architecture:** Three layers in one module with one-directional dependencies — `presentation` (Compose + ViewModels) → `domain` (pure Kotlin models, repository interfaces, use cases) → `data` (Retrofit, Room, repository implementations). Repository interfaces live in `domain` and are implemented in `data`, so the domain layer is testable without Android. Caching is tiered per §7 of the spec, not uniform.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, KSP, Retrofit + OkHttp, kotlinx.serialization, Room, Coil, Navigation Compose (type-safe routes), Coroutines/Flow, JUnit4 + Turbine + Robolectric.

**Spec:** `alfaflix-design.md`

## Global Constraints

Every task's requirements implicitly include this section.

- Application ID and package root: `com.application.alfaflix`
- Display name: **Alfaflix**
- `minSdk 25`, `compileSdk 37`, `targetSdk 37`, `jvmTarget 17`
- Single Gradle module. Do not create `:core`, `:data`, or `:feature` modules.
- `domain/` contains **zero** Android imports, zero Retrofit, zero Room.
- ViewModels never touch a DAO, a Retrofit service, or a DTO. They depend on use cases
  where there is real logic, and on domain repository *interfaces* for plain observable
  reads (see the spec §4.1 — this was revised during implementation to avoid pass-throughs).
- DTOs never leave `data/`. Domain models never carry API-shaped fields.
- No `GlobalScope`. No network or database calls inside Composables.
- The OMDb API key comes from `local.properties` → `BuildConfig`. Never hardcoded.
- Search debounce: **400 ms**. Minimum query length: **2**.
- Detail cache TTL: **7 days**. Collection cache TTL: **24 hours**.
- Recently Viewed cap: **20 rows**.
- Exactly **3** curated collections. Section header: "Featured Collections". Never label a row "Trending", "Popular", or "Top Rated".
- Search results are **never** written to Room.
- All user-facing copy comes from `strings.xml`, never hardcoded in Composables.
- Every icon-only control carries a meaningful `contentDescription`.
- Tests run on the JVM: `./gradlew test`. Room and Compose tests use Robolectric.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `core/result/AppResult.kt` | `AppResult`, `AppError` |
| `core/result/Cached.kt` | `Cached<T>`, `RefreshState` |
| `core/time/TimeProvider.kt` | Injectable clock so TTL is testable |
| `core/network/NetworkMonitor.kt` | Connectivity as `Flow<Boolean>` |
| `ui/theme/`, `ui/components/` | Theme, tokens, reusable components (kept beside the scaffold's existing `ui/theme`) |
| `data/remote/api/OmdbApi.kt` | Retrofit interface, 2 endpoints |
| `data/remote/dto/*.kt` | `SearchResponseDto`, `SearchItemDto`, `MovieDetailDto`, `RatingDto` |
| `data/remote/mapper/MovieMappers.kt` | DTO → domain, `"N/A"` normalisation |
| `data/remote/MovieRemoteDataSource.kt` | Wraps Retrofit, maps OMDb HTTP-200 failures |
| `data/local/entity/*.kt` | 5 Room entities |
| `data/local/dao/*.kt` | 4 DAOs |
| `data/local/database/AlfaflixDatabase.kt` | Room DB + `TypeConverters` |
| `data/local/mapper/EntityMappers.kt` | entity ↔ domain |
| `domain/model/CuratedCollections.kt` | The 3 static collections (moved to `domain`: it is product content, and `domain` must not import from `data`) |
| `data/repository/*.kt` | 3 repository implementations |
| `domain/model/*.kt` | `Movie`, `MovieDetail`, `MovieType`, `Rating`, `SearchPage`, `CuratedCollection` |
| `domain/repository/*.kt` | 3 repository interfaces |
| `domain/usecase/*.kt` | 4 use cases (3 proposed ones were dropped as pass-throughs) |
| `presentation/navigation/` | Routes, NavHost, bottom bar |
| `presentation/{home,search,detail,favorites}/` | Screen + ViewModel + state per feature |
| `di/*.kt` | Hilt modules |

---

## Task 1: Foundation — build config and dependencies ✅ COMPLETE

**Goal:** A green build with every dependency wired, before any feature code exists.

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/application/alfaflix/AlfaflixApplication.kt`

**Interfaces:**
- Produces: `BuildConfig.OMDB_API_KEY: String`, `BuildConfig.OMDB_BASE_URL: String`, `@HiltAndroidApp class AlfaflixApplication`

**Outcome — the toolchain fight, recorded because it is not guessable:**

The scaffold shipped an internally inconsistent build: AGP 8.13.2 paired with AndroidX
libraries (`core-ktx` 1.19.0, `lifecycle` 2.11.0) that require **AGP 9.1.0+ and compileSdk 37**.
The baseline build failed before a line of feature code existed. Resolving it took four
iterations, each fixing a real incompatibility:

1. AGP 9 rejects `org.jetbrains.kotlin.android` — Kotlin support is now built in.
2. But **KSP is incompatible with AGP's built-in Kotlin**, and Hilt and Room both need KSP
   → `android.builtInKotlin=false` plus an explicit KGP.
3. KGP then fails against AGP 9's new DSL (`ApplicationExtensionImpl cannot be cast to
   BaseExtension`) → `android.newDsl=false`.
4. KSP's versioning scheme has changed: it no longer uses `<kotlin>-<ksp>` (2.2.21-2.0.5)
   but an independent line (2.3.11), which lifted the apparent Kotlin 2.2.21 ceiling and
   allowed Kotlin 2.4.20.

Also: `platforms;android-37` does not exist. Android now uses **minor-versioned SDK
packages** — the correct package is `platforms;android-37.0`.

Final verified stack: AGP 9.4.0, Gradle 9.7.1, Kotlin 2.4.20, KSP 2.3.11, Hilt 2.60.1,
Room 2.8.4, Compose BOM 2026.08.00, Retrofit 3.0.0, OkHttp 5.5.0, Coil 3.6.2,
compileSdk/targetSdk 37, minSdk 25, jvmTarget 17.

**Decision:** the package was **not** renamed. The app keeps `com.application.alfaflix`
and the Alfaflix product name, matching the existing GitHub remote.

- [x] **Step 1: Keep the existing package**

No rename. `namespace`/`applicationId` stay `com.application.alfaflix`; `rootProject.name`
stays `alfaflix`. The two `Example*Test.kt` scaffold tests were deleted — they test nothing.

- [x] **Step 2: Modernise the build config**

In `app/build.gradle.kts`: set `sourceCompatibility`/`targetCompatibility` to `VERSION_17`; replace the deprecated `kotlinOptions { jvmTarget = "11" }` block with:

```kotlin
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
```

Enable `buildFeatures { compose = true; buildConfig = true }` and add:

```kotlin
testOptions { unitTests { isIncludeAndroidResources = true } }
```

- [x] **Step 3: Plumb the API key from local.properties**

In `app/build.gradle.kts`, above the `android { }` block:

```kotlin
val localProps = java.util.Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val omdbApiKey: String = localProps.getProperty("OMDB_API_KEY")
    ?: System.getenv("OMDB_API_KEY")
    ?: ""
```

In `defaultConfig`:

```kotlin
buildConfigField("String", "OMDB_API_KEY", "\"$omdbApiKey\"")
buildConfigField("String", "OMDB_BASE_URL", "\"https://www.omdbapi.com/\"")
```

The build must not fail on an empty key — that would block compilation for a reviewer who has not configured one. Instead, fail loudly at runtime: the network module throws a clear `IllegalStateException("OMDB_API_KEY is not configured — see README Setup")` when the key is blank.

- [x] **Step 4: Add release signing with debug fallback**

So `assembleRelease` always yields an installable APK without committing a keystore:

```kotlin
signingConfigs {
    create("release") {
        val storePath = localProps.getProperty("RELEASE_STORE_FILE")
        if (storePath != null) {
            storeFile = file(storePath)
            storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
        }
    }
}
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        signingConfig = if (localProps.getProperty("RELEASE_STORE_FILE") != null)
            signingConfigs.getByName("release") else signingConfigs.getByName("debug")
    }
}
```

Add keep rules to `proguard-rules.pro` for kotlinx.serialization (Retrofit, Room, Hilt and Coil ship consumer rules).

- [x] **Step 5: Wire dependencies in the version catalog**

Add to `libs.versions.toml` and `app/build.gradle.kts`: KSP, Hilt (+ `hilt-navigation-compose`), Room (`runtime`, `ksp`, `testing`), Retrofit + `retrofit2-kotlinx-serialization-converter`, OkHttp + `logging-interceptor`, `kotlinx-serialization-json`, Coil Compose, `navigation-compose`, `lifecycle-runtime-compose` (for `collectAsStateWithLifecycle`), `lifecycle-viewmodel-compose`, `material-icons-extended`, and test deps: `kotlinx-coroutines-test`, `turbine`, `robolectric`, `androidx-test-core`, `compose-ui-test-junit4`.

Bump the Compose BOM off `2024.09.00`. **Resolve versions empirically** — the scaffold pairs AGP 8.13.2 with Kotlin 2.0.21, and KSP must match Kotlin exactly (`<kotlin>-<ksp>`). If the toolchain forces a Kotlin bump, take it now.

- [x] **Step 6: Add the application class and manifest entries**

```kotlin
@HiltAndroidApp
class AlfaflixApplication : Application()
```

In `AndroidManifest.xml`: add `<uses-permission android:name="android.permission.INTERNET" />`, `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`, and `android:name=".AlfaflixApplication"` on `<application>`. Set `android:label="Alfaflix"` in `strings.xml`.

- [x] **Step 7: Verify the build is green — this is the task's real deliverable**

Run: `./gradlew clean assembleDebug test --no-daemon`
Expected: `BUILD SUCCESSFUL`. Do not proceed to Task 2 until both succeed.

- [x] **Checkpoint** — suggested message: `chore: modernize build, rename package to alfaflix, wire dependencies`

---

## Task 2: Core primitives

**Files:**
- Create: `core/result/AppResult.kt`, `core/result/Cached.kt`, `core/time/TimeProvider.kt`, `core/network/NetworkMonitor.kt`
- Test: `test/.../core/` as needed

**Interfaces:**
- Produces:

```kotlin
sealed interface AppError {
    data object Offline : AppError
    data object Timeout : AppError
    data class Server(val code: Int) : AppError
    data class Api(val message: String) : AppError
    data object Unknown : AppError
}

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R>

sealed interface RefreshState {
    data object Idle : RefreshState
    data object InProgress : RefreshState
    data class Failed(val error: AppError) : RefreshState
}

data class Cached<T>(
    val data: T?,
    val lastUpdatedAt: Long?,
    val refresh: RefreshState
)

interface TimeProvider { fun nowMillis(): Long }
class SystemTimeProvider @Inject constructor() : TimeProvider

interface NetworkMonitor { val isOnline: Flow<Boolean> }
```

- [x] **Step 1: Implement the value types** — `AppResult`, `AppError`, `Cached`, `RefreshState`, `TimeProvider`.

- [x] **Step 2: Implement `NetworkMonitorImpl`**

Wrap `ConnectivityManager` in a `callbackFlow` registering a `NetworkCallback`, checking `NET_CAPABILITY_INTERNET` **and** `NET_CAPABILITY_VALIDATED`. Unregister in `awaitClose`. Apply `.distinctUntilChanged()` and `.conflate()`.

- [x] **Step 3: Verify** — Run: `./gradlew compileDebugKotlin`. Expected: `BUILD SUCCESSFUL`.

- [x] **Checkpoint** — `feat: add core result, cache and connectivity primitives`

---

## Task 3: Remote layer — API, DTOs, mappers (TDD)

Mappers are the highest-value test target in the project. Write the tests first.

**Files:**
- Create: `data/remote/api/OmdbApi.kt`, `data/remote/dto/OmdbDtos.kt`, `data/remote/mapper/MovieMappers.kt`, `data/remote/MovieRemoteDataSource.kt`
- Create: `domain/model/Movie.kt`, `MovieDetail.kt`, `MovieType.kt`, `Rating.kt`, `SearchPage.kt`
- Create: `di/NetworkModule.kt`
- Test: `test/.../data/remote/mapper/MovieMappersTest.kt`

**Interfaces:**
- Consumes: `AppResult`, `AppError` (Task 2)
- Produces:

```kotlin
interface OmdbApi {
    @GET("/") suspend fun search(
        @Query("apikey") apiKey: String,
        @Query("s") query: String,
        @Query("page") page: Int,
        @Query("type") type: String?
    ): SearchResponseDto

    @GET("/") suspend fun detail(
        @Query("apikey") apiKey: String,
        @Query("i") imdbId: String,
        @Query("plot") plot: String = "full"
    ): MovieDetailDto
}

class MovieRemoteDataSource {
    suspend fun search(query: String, page: Int, type: MovieType?): AppResult<SearchPage>
    suspend fun detail(imdbId: String): AppResult<MovieDetail>
}

fun SearchItemDto.toDomain(): Movie
fun MovieDetailDto.toDomain(): MovieDetail
```

- [x] **Step 1: Write the failing mapper tests**

```kotlin
@Test fun `N/A poster maps to null`() {
    val dto = SearchItemDto(imdbID = "tt1", Title = "T", Year = "2008", Type = "movie", Poster = "N/A")
    assertNull(dto.toDomain().posterUrl)
}

@Test fun `runtime string parses to minutes`() {
    assertEquals(142, detailDto(Runtime = "142 min").toDomain().runtimeMinutes)
}

@Test fun `unparseable runtime becomes null rather than throwing`() {
    assertNull(detailDto(Runtime = "N/A").toDomain().runtimeMinutes)
}

@Test fun `imdb votes strip thousands separators`() {
    assertEquals(2845132, detailDto(imdbVotes = "2,845,132").toDomain().imdbVotes)
}

@Test fun `genres split and trim`() {
    assertEquals(listOf("Action", "Crime", "Drama"), detailDto(Genre = "Action, Crime, Drama").toDomain().genres)
}

@Test fun `N/A list field becomes empty list not a list containing N slash A`() {
    assertEquals(emptyList<String>(), detailDto(Genre = "N/A").toDomain().genres)
}

@Test fun `unknown type maps to UNKNOWN not a crash`() {
    assertEquals(MovieType.UNKNOWN, searchItemDto(Type = "podcast").toDomain().type)
}
```

- [x] **Step 2: Run to verify they fail** — Run: `./gradlew testDebugUnitTest --tests "*MovieMappersTest*"`. Expected: compilation failure (types not defined).

- [x] **Step 3: Implement DTOs, domain models and mappers**

DTOs use `@Serializable` with OMDb's exact PascalCase field names. Central helper:

```kotlin
private fun String?.orNullIfNa(): String? =
    this?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }

private fun String?.toStringList(): List<String> =
    orNullIfNa()?.split(",")?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
```

Every parse is total — no mapper throws on malformed input.

- [x] **Step 4: Run tests to verify they pass** — Expected: PASS.

- [x] **Step 5: Implement `MovieRemoteDataSource` with OMDb's HTTP-200 failure handling**

Inspect `Response` before anything else. Map per spec §9.1: `"Movie not found!"` → `Success(SearchPage(emptyList(), 0, page))`; `"Too many results."` → `Success` empty with a hint flag; anything else → `Failure(AppError.Api(message))`. Catch `UnknownHostException`/`ConnectException` → `AppError.Offline`, `SocketTimeoutException` → `AppError.Timeout`, `HttpException` → `AppError.Server(code)`.

- [x] **Step 6: Write the data-source failure-mapping tests** — cover each row of the §9.1 table using a fake `OmdbApi`. Assert "Movie not found!" produces `Success` with an empty list, **not** `Failure`.

- [x] **Step 7: Implement `NetworkModule`**

Provide `Json { ignoreUnknownKeys = true }`, `OkHttpClient` with a 10 MB `Cache`, `HttpLoggingInterceptor` at `BODY` for debug and `NONE` for release, and `Retrofit` with `BuildConfig.OMDB_BASE_URL`. Throw the actionable `IllegalStateException` from Task 1 Step 3 if `BuildConfig.OMDB_API_KEY.isBlank()`.

- [x] **Step 8: Verify** — Run: `./gradlew test`. Expected: all green.

- [x] **Checkpoint** — `feat: implement OMDb remote data source with domain mappers`

---

## Task 4: Local layer — Room entities, DAOs, database (TDD, Robolectric)

**Files:**
- Create: `data/local/entity/{FavoriteMovieEntity,RecentlyViewedEntity,MovieDetailEntity,CollectionMovieEntity,CollectionRefreshEntity}.kt`
- Create: `data/local/dao/{FavoriteDao,RecentlyViewedDao,MovieDetailDao,CollectionDao}.kt`
- Create: `data/local/database/AlfaflixDatabase.kt`, `data/local/database/Converters.kt`
- Create: `data/local/mapper/EntityMappers.kt`
- Create: `di/DatabaseModule.kt`
- Test: `test/.../data/local/{FavoriteDaoTest,RecentlyViewedDaoTest}.kt`

**Interfaces:**
- Produces:

```kotlin
@Dao interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteMovieEntity>>
    @Query("SELECT imdbId FROM favorites")
    fun observeIds(): Flow<List<String>>
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE imdbId = :imdbId)")
    suspend fun exists(imdbId: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(e: FavoriteMovieEntity)
    @Query("DELETE FROM favorites WHERE imdbId = :imdbId") suspend fun delete(imdbId: String)
}

@Dao interface RecentlyViewedDao {
    @Query("SELECT * FROM recently_viewed ORDER BY viewedAt DESC")
    fun observeAll(): Flow<List<RecentlyViewedEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: RecentlyViewedEntity)
    @Query("DELETE FROM recently_viewed WHERE imdbId NOT IN (SELECT imdbId FROM recently_viewed ORDER BY viewedAt DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}
```

`MovieDetailDao`: `observe(imdbId): Flow<MovieDetailEntity?>`, `upsert`, and `@Query("SELECT * FROM collection_movies ORDER BY RANDOM() LIMIT 1")` lives on `CollectionDao` as `randomMovie(): CollectionMovieEntity?` for Surprise Me.

`CollectionDao`: `observeCollection(id): Flow<List<CollectionMovieEntity>>`, `replaceCollection(id, rows)` as a `@Transaction` (delete-then-insert so removed titles do not linger), `refreshedAt(id): Long?`, `setRefreshedAt(id, ts)`.

- [x] **Step 1: Write the failing DAO tests (Robolectric, in-memory Room)**

```kotlin
@RunWith(RobolectricTestRunner::class)
class RecentlyViewedDaoTest {
    private lateinit var db: AlfaflixDatabase
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AlfaflixDatabase::class.java
        ).allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    @Test fun `re-viewing a movie does not duplicate it and moves it to the top`() = runTest {
        val dao = db.recentlyViewedDao()
        dao.upsert(entity("tt1", "Batman Begins", viewedAt = 1))
        dao.upsert(entity("tt2", "Interstellar", viewedAt = 2))
        dao.upsert(entity("tt1", "Batman Begins", viewedAt = 3))

        val rows = dao.observeAll().first()
        assertEquals(2, rows.size)
        assertEquals("tt1", rows.first().imdbId)
    }

    @Test fun `trim keeps exactly the newest 20 rows`() = runTest {
        val dao = db.recentlyViewedDao()
        repeat(25) { dao.upsert(entity("tt$it", "M$it", viewedAt = it.toLong())) }
        dao.trimTo(20)
        val rows = dao.observeAll().first()
        assertEquals(20, rows.size)
        assertEquals("tt24", rows.first().imdbId)
        assertTrue(rows.none { it.imdbId == "tt0" })
    }
}
```

Plus `FavoriteDaoTest`: insert then `observeIds()` emits the ID; delete then it disappears; `exists()` reflects both.

- [x] **Step 2: Run to verify they fail** — Run: `./gradlew testDebugUnitTest --tests "*DaoTest*"`. Expected: compilation failure.

- [x] **Step 3: Implement entities, converters, DAOs and the database**

`Converters` uses `kotlinx.serialization` for `List<String>` and `List<Rating>`. Entity table names: `favorites`, `recently_viewed`, `movie_details`, `collection_movies`, `collection_refresh`. `CollectionMovieEntity` uses `primaryKeys = ["collectionId", "imdbId"]` with `indices = [Index("collectionId")]`.

- [x] **Step 4: Run tests to verify they pass** — Expected: PASS. **If Robolectric proves unstable here, stop and report it** — spec §17 permits falling back to `androidTest/`, but that decision must be surfaced, not made silently.

- [x] **Step 5: Implement `DatabaseModule`** — provide the database and each DAO. No `fallbackToDestructiveMigration` hand-waving: version 1, no migrations needed yet.

- [x] **Checkpoint** — `feat: add Room persistence for favorites, history and cached movies`

---

## Task 5: Repositories — favorites and recently viewed

**Files:**
- Create: `domain/repository/{FavoritesRepository,RecentlyViewedRepository}.kt`
- Create: `data/repository/{FavoritesRepositoryImpl,RecentlyViewedRepositoryImpl}.kt`
- Create: `di/RepositoryModule.kt`
- Test: `test/.../data/repository/RecentlyViewedRepositoryTest.kt`

**Interfaces:**
- Produces:

```kotlin
interface FavoritesRepository {
    fun observeFavorites(): Flow<List<Movie>>
    fun observeFavoriteIds(): Flow<Set<String>>
    suspend fun isFavorite(imdbId: String): Boolean
    suspend fun add(movie: Movie)
    suspend fun remove(imdbId: String)
}

interface RecentlyViewedRepository {
    fun observeRecentlyViewed(): Flow<List<Movie>>
    suspend fun record(movie: Movie)
}
```

- [x] **Step 1: Write the failing test** — `record()` upserts then trims to 20, driven by an injected `TimeProvider` so `viewedAt` is deterministic.

- [x] **Step 2: Run to verify it fails.**

- [x] **Step 3: Implement both repositories** — `observeFavoriteIds()` maps the DAO's `List<String>` to a `Set<String>` and applies `.distinctUntilChanged()`. `record()` calls `upsert` then `trimTo(20)`.

- [x] **Step 4: Run tests to verify they pass.**

- [x] **Checkpoint** — `feat: implement favorites and recently viewed repositories`

---

## Task 6: MovieRepository — tiered caching

The centrepiece of the offline story. Cache-first with TTL-gated refresh.

**Files:**
- Create: `domain/repository/MovieRepository.kt`, `data/repository/MovieRepositoryImpl.kt`
- Create: `data/collection/CuratedCollections.kt`
- Create: `domain/model/CuratedCollection.kt`
- Test: `test/.../data/repository/MovieRepositoryTest.kt`

**Interfaces:**
- Produces:

```kotlin
interface MovieRepository {
    suspend fun search(query: String, page: Int, type: MovieType?): AppResult<SearchPage>
    fun observeMovieDetail(imdbId: String): Flow<Cached<MovieDetail>>
    suspend fun refreshMovieDetail(imdbId: String, force: Boolean): AppResult<Unit>
    fun observeCollection(collectionId: String): Flow<Cached<List<Movie>>>
    suspend fun refreshCollections(force: Boolean): AppResult<Unit>
    suspend fun randomCachedMovie(): Movie?
}

data class CuratedCollection(val id: String, val title: String, val query: String, val type: MovieType)
```

`CuratedCollections.ALL` — exactly three, per spec §12.3:

```kotlin
val ALL = listOf(
    CuratedCollection("caped_crusaders", "Caped Crusaders", "batman", MovieType.MOVIE),
    CuratedCollection("galaxy_far_away", "A Galaxy Far, Far Away", "star wars", MovieType.MOVIE),
    CuratedCollection("middle_earth", "Return to Middle-earth", "lord of the rings", MovieType.MOVIE),
)
```

- [x] **Step 1: Write the failing caching tests**

```kotlin
@Test fun `fresh cache makes no network call`() = runTest {
    fakeTime.now = 1_000L
    dao.upsert(detailEntity("tt1", cachedAt = 1_000L - ONE_DAY))   // TTL is 7 days
    repo.refreshMovieDetail("tt1", force = false)
    assertEquals(0, fakeRemote.detailCallCount)
}

@Test fun `stale cache triggers a refresh`() = runTest {
    fakeTime.now = 1_000L + EIGHT_DAYS
    dao.upsert(detailEntity("tt1", cachedAt = 1_000L))
    repo.refreshMovieDetail("tt1", force = false)
    assertEquals(1, fakeRemote.detailCallCount)
}

@Test fun `force bypasses a fresh TTL`() = runTest {
    dao.upsert(detailEntity("tt1", cachedAt = fakeTime.now))
    repo.refreshMovieDetail("tt1", force = true)
    assertEquals(1, fakeRemote.detailCallCount)
}

@Test fun `refresh failure with a warm cache keeps the data and reports the failure`() = runTest {
    dao.upsert(detailEntity("tt1", cachedAt = 0L))
    fakeRemote.detailResult = AppResult.Failure(AppError.Offline)
    repo.observeMovieDetail("tt1").test {
        val emission = expectMostRecentItem()
        assertNotNull(emission.data)                              // content survives
        assertEquals(RefreshState.Failed(AppError.Offline), emission.refresh)
    }
}

@Test fun `refresh failure with a cold cache surfaces null data and the error`() = runTest {
    fakeRemote.detailResult = AppResult.Failure(AppError.Offline)
    repo.observeMovieDetail("tt404").test {
        val emission = expectMostRecentItem()
        assertNull(emission.data)
        assertTrue(emission.refresh is RefreshState.Failed)
    }
}

@Test fun `search never writes to the database`() = runTest {
    repo.search("batman", page = 1, type = MovieType.MOVIE)
    assertEquals(0, db.collectionDao().observeCollection("caped_crusaders").first().size)
}
```

- [x] **Step 2: Run to verify they fail.**

- [x] **Step 3: Implement `MovieRepositoryImpl`**

`observeMovieDetail` returns the DAO flow mapped into `Cached<MovieDetail>`, combined with a `MutableStateFlow<RefreshState>` the repository updates while refreshing. `search()` calls the remote source and **never touches Room**. `refreshCollections` iterates `CuratedCollections.ALL`, and a per-collection failure must not abort the others — collect results and return `Failure` only if every collection failed.

- [x] **Step 4: Run tests to verify they pass.**

- [x] **Checkpoint** — `feat: implement offline-first movie repository with tiered caching`

---

## Task 7: Use cases

**Files:**
- Create: `domain/usecase/{SearchMoviesUseCase,ObserveMovieDetailUseCase,ObserveHomeFeedUseCase,ToggleFavoriteUseCase,RecordMovieViewUseCase,ObserveFavoritesUseCase,GetSurpriseMovieUseCase}.kt`
- Create: `domain/model/{MovieListItem,HomeFeed,CollectionRow}.kt`
- Test: `test/.../domain/usecase/{ToggleFavoriteUseCaseTest,RecordMovieViewUseCaseTest,ObserveHomeFeedUseCaseTest}.kt`

**Interfaces:**
- Produces:

```kotlin
data class MovieListItem(val movie: Movie, val isFavorite: Boolean)
data class CollectionRow(val id: String, val title: String, val items: List<MovieListItem>)
data class HomeFeed(
    val recentlyViewed: List<MovieListItem>,
    val collections: List<CollectionRow>,
    val lastUpdatedAt: Long?,
    val refresh: RefreshState
)

class SearchMoviesUseCase  { suspend operator fun invoke(query: String, page: Int): AppResult<SearchPage> }
class ObserveMovieDetailUseCase { operator fun invoke(imdbId: String): Flow<Pair<Cached<MovieDetail>, Boolean>> }
class ObserveHomeFeedUseCase { operator fun invoke(): Flow<HomeFeed> }
class ToggleFavoriteUseCase { suspend operator fun invoke(movie: Movie) }
class RecordMovieViewUseCase { suspend operator fun invoke(movie: Movie) }
class ObserveFavoritesUseCase { operator fun invoke(): Flow<List<MovieListItem>> }
class GetSurpriseMovieUseCase { suspend operator fun invoke(): Movie? }
```

- [x] **Step 1: Write the failing use-case tests**

```kotlin
@Test fun `toggle adds when absent and removes when present`() = runTest {
    val movie = movie("tt1")
    toggle(movie); assertTrue(favorites.isFavorite("tt1"))
    toggle(movie); assertFalse(favorites.isFavorite("tt1"))
}

@Test fun `surprise me returns null when the cache is empty`() = runTest {
    assertNull(getSurpriseMovie())
}

@Test fun `home feed marks favorited movies across every collection row`() = runTest {
    favorites.add(movie("tt1"))
    val feed = observeHomeFeed().first()
    assertTrue(feed.collections.flatMap { it.items }.filter { it.movie.imdbId == "tt1" }.all { it.isFavorite })
}
```

- [x] **Step 2: Run to verify they fail.**

- [x] **Step 3: Implement the use cases** — `ObserveHomeFeedUseCase` combines the three collection flows + recently viewed + `observeFavoriteIds()`. `SearchMoviesUseCase` combines search results with favorite IDs. No use case is a bare pass-through.

- [x] **Step 4: Run tests to verify they pass.**

- [x] **Checkpoint** — `feat: add domain use cases`

---

## Task 8: Design system

**Files:**
- Create/modify: `core/designsystem/theme/{Color,Type,Theme,Spacing}.kt`
- Create: `core/designsystem/component/{PosterImage,MovieCard,MoviePosterCard,ShimmerBox,EmptyState,ErrorState,StaleDataBanner,SectionHeader}.kt`
- Modify: `res/values/strings.xml`

**Interfaces:**
- Produces: `@Composable fun PosterImage(url: String?, contentDescription: String?, modifier: Modifier)`, `EmptyState(icon, title, subtitle)`, `ErrorState(error: AppError, onRetry: () -> Unit)`, `StaleDataBanner(staleness: Staleness, onRetry: () -> Unit)`, `MoviePosterCard(item: MovieListItem, onClick, onToggleFavorite)`

- [x] **Step 1: Build a cinematic Material 3 theme** — dark-first palette with a correct light counterpart, `dynamicColor` off so branding is consistent. Define a `Spacing` object; no magic dp literals in screens.

- [x] **Step 2: Implement `PosterImage`** — Coil `AsyncImage` with a shimmer placeholder, an explicit error fallback (icon + title initial, never a broken layout), fixed 2:3 aspect ratio so a missing poster cannot collapse the row. `crossfade(true)`.

- [x] **Step 3: Implement the state components** — `ErrorState` renders `error.asMessage()` and a retry button. `StaleDataBanner` is a subtle inline surface, not a blocking dialog, rendering "Showing saved data · updated {relative}" per spec §7.1.

- [x] **Step 4: Add every user-facing string to `strings.xml`** — including the four empty-state strings from spec §12.4 and the `AppError` messages.

- [x] **Step 5: Verify** — Run: `./gradlew assembleDebug`. Expected: `BUILD SUCCESSFUL`.

- [x] **Checkpoint** — `feat: add cinematic design system and shared components`

---

## Task 9: Navigation

**Files:**
- Create: `presentation/navigation/{Routes,AlfaflixNavHost,BottomBar}.kt`
- Modify: `MainActivity.kt`

**Interfaces:**
- Produces:

```kotlin
@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object FavoritesRoute
@Serializable data class MovieDetailRoute(val imdbId: String)
```

- [x] **Step 1: Implement type-safe routes and the NavHost** — `composable<MovieDetailRoute>`, read via `SavedStateHandle.toRoute<MovieDetailRoute>()` inside the ViewModel. Only `imdbId` crosses the boundary.

- [x] **Step 2: Implement the bottom bar** — three destinations, hidden on Detail. Preserve back-stack state with `saveState`/`restoreState` and `launchSingleTop`. Each item needs a `contentDescription`.

- [x] **Step 3: Wire `MainActivity`** — `@AndroidEntryPoint`, `enableEdgeToEdge()`, theme, NavHost.

- [x] **Step 4: Verify** — `./gradlew assembleDebug`.

- [x] **Checkpoint** — `feat: add type-safe navigation with bottom navigation`

---

## Task 10: Search — ViewModel (TDD, the correctness centrepiece)

**Files:**
- Create: `presentation/search/{SearchViewModel,SearchUiState}.kt`
- Test: `test/.../presentation/search/SearchViewModelTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class SearchUiState(
    val query: String = "",
    val stage: Stage = Stage.Idle,
    val movies: List<MovieListItem> = emptyList(),
    val totalResults: Int = 0,
    val canLoadMore: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val nextPageError: AppError? = null,
    val isOffline: Boolean = false
) { sealed interface Stage { Idle; Loading; Content; Empty; Error(val error: AppError) } }

class SearchViewModel {
    val uiState: StateFlow<SearchUiState>
    fun onQueryChange(q: String)
    fun loadNextPage()
    fun retry()
}
```

- [x] **Step 1: Write the failing ViewModel tests**

```kotlin
@Test fun `rapid typing issues exactly one request`() = runTest {
    listOf("b","ba","bat","batm","batma","batman").forEach {
        vm.onQueryChange(it); advanceTimeBy(50)
    }
    advanceTimeBy(500)                       // cross the 400 ms debounce once
    assertEquals(1, fakeRepo.searchCallCount)
    assertEquals("batman", fakeRepo.lastQuery)
}

@Test fun `queries shorter than two characters never hit the network`() = runTest {
    vm.onQueryChange("b"); advanceTimeBy(1000)
    assertEquals(0, fakeRepo.searchCallCount)
    assertEquals(Stage.Idle, vm.uiState.value.stage)
}

@Test fun `no results maps to Empty not Error`() = runTest {
    fakeRepo.searchResult = AppResult.Success(SearchPage(emptyList(), 0, 1))
    vm.onQueryChange("zzzzz"); advanceTimeBy(500)
    assertEquals(Stage.Empty, vm.uiState.value.stage)
}

@Test fun `pagination appends and stops at totalResults`() = runTest {
    fakeRepo.searchResult = AppResult.Success(SearchPage(tenMovies(), totalResults = 15, page = 1))
    vm.onQueryChange("batman"); advanceTimeBy(500)
    fakeRepo.searchResult = AppResult.Success(SearchPage(fiveMovies(), totalResults = 15, page = 2))
    vm.loadNextPage(); advanceUntilIdle()
    assertEquals(15, vm.uiState.value.movies.size)
    assertFalse(vm.uiState.value.canLoadMore)
}

@Test fun `a second loadNextPage while one is in flight does not duplicate the request`() = runTest {
    vm.onQueryChange("batman"); advanceTimeBy(500)
    fakeRepo.blockNextCall()
    vm.loadNextPage(); vm.loadNextPage(); vm.loadNextPage()
    advanceUntilIdle()
    assertEquals(2, fakeRepo.searchCallCount)      // page 1 + exactly one page 2
}

// SPEC §13.1 REGRESSION TEST — the explicit correctness requirement
@Test fun `results from an old query never append to a newer query`() = runTest {
    fakeRepo.searchResult = AppResult.Success(SearchPage(batmanMovies(), 100, 1))
    vm.onQueryChange("batman"); advanceTimeBy(500)

    fakeRepo.blockNextCall()                       // page 2 of "batman" hangs
    vm.loadNextPage()

    fakeRepo.searchResult = AppResult.Success(SearchPage(inceptionMovies(), 1, 1))
    vm.onQueryChange("inception"); advanceTimeBy(500)
    fakeRepo.releaseBlockedCall()                  // stale page 2 now returns
    advanceUntilIdle()

    assertTrue(vm.uiState.value.movies.none { it.movie.title.contains("Batman") })
    assertEquals("inception", vm.uiState.value.query)
}
```

- [x] **Step 2: Run to verify they fail.**

- [x] **Step 3: Implement `SearchViewModel`**

The debounce chain per spec §13. Both §13.1 guards are required:

```kotlin
private var nextPageJob: Job? = null
private var currentQueryToken: String = ""

init {
    queryFlow
        .debounce(DEBOUNCE_MS)
        .map(String::trim)
        .distinctUntilChanged()
        .onEach { nextPageJob?.cancel() }          // guard 1: cancellation
        .flatMapLatest { q -> loadFirstPage(q) }
        .launchIn(viewModelScope)
}

// guard 2: token validation before any append
private fun appendPage(token: String, page: SearchPage) {
    if (token != currentQueryToken) return
    _uiState.update { /* append */ }
}
```

Inject the dispatcher so tests control virtual time. `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.

- [x] **Step 4: Run tests to verify they pass** — all seven, especially the §13.1 regression test.

- [x] **Checkpoint** — `feat: implement live search with debounce and safe pagination`

---

## Task 11: Search — screen

**Files:**
- Create: `presentation/search/SearchScreen.kt`

- [x] **Step 1: Implement the screen** — `LazyVerticalGrid` with `key = { it.movie.imdbId }`, `contentType` set. Search field with a clear button (`contentDescription` on both). Trigger `loadNextPage()` from a `derivedStateOf` on the last visible index, never from raw scroll offset.

- [x] **Step 2: Handle every stage** — Idle → "Find your next movie"; Loading → skeleton grid preserving geometry; Empty → "No movies found"; Error → `ErrorState` with retry; `nextPageError` → inline footer row with retry that never replaces the list.

- [x] **Step 3: Handle offline** — when `isOffline` and there are no results, the empty state routes the user to Favorites and Recently Viewed (spec §16), rather than showing a dead end.

- [x] **Step 4: Verify** — `./gradlew assembleDebug`.

- [x] **Checkpoint** — `feat: add search screen`

---

## Task 12: Home — ViewModel and screen

**Files:**
- Create: `presentation/home/{HomeViewModel,HomeUiState,HomeScreen}.kt`
- Test: `test/.../presentation/home/HomeViewModelTest.kt`

- [x] **Step 1: Write the failing tests** — cold start with an empty cache → `Loading`; a warm cache → `Content` immediately **without** passing through `Loading`; refresh failure with a warm cache → `Content` retained with non-null `staleness`; refresh failure with a cold cache → `Error`.

- [x] **Step 2: Run to verify they fail.**

- [x] **Step 3: Implement `HomeViewModel`** — consumes `ObserveHomeFeedUseCase`, `GetSurpriseMovieUseCase`, `ToggleFavoriteUseCase`, `NetworkMonitor`. `refresh(force = true)` for pull-to-refresh. Derive `Staleness` from `HomeFeed.lastUpdatedAt` + `refresh`.

- [x] **Step 4: Run tests to verify they pass.**

- [x] **Step 5: Implement `HomeScreen`** — `PullToRefreshBox` wrapping a `LazyColumn`; Recently Viewed row first (omitted entirely when empty), then the "Featured Collections" header and three `LazyRow` carousels, each with stable keys. `StaleDataBanner` above the content when `staleness != null`. Surprise Me in the top bar, disabled with an explanatory `contentDescription` when the cached pool is empty.

- [x] **Step 6: Verify** — `./gradlew test assembleDebug`.

- [x] **Checkpoint** — `feat: add home screen with curated collections and recently viewed`

---

## Task 13: Detail — ViewModel and screen

**Files:**
- Create: `presentation/detail/{MovieDetailViewModel,MovieDetailUiState,MovieDetailScreen}.kt`
- Test: `test/.../presentation/detail/MovieDetailViewModelTest.kt`

- [x] **Step 1: Write the failing tests** — reads `imdbId` from `SavedStateHandle`; records the view exactly **once** even across multiple state emissions; favorite toggle flips `isFavorite`; refresh failure with a warm cache keeps `Content` and sets `staleness`.

- [x] **Step 2: Run to verify they fail.**

- [x] **Step 3: Implement `MovieDetailViewModel`** — guard the view recording with a `hasRecorded` flag so re-emission does not re-record.

- [x] **Step 4: Run tests to verify they pass.**

- [x] **Step 5: Implement `MovieDetailScreen`** — hierarchy per spec §12.3. **Every optional field is wrapped in a null/empty check** so absent data is omitted, never rendered as "N/A". Collapsing hero poster with a scrim; favorite FAB whose `contentDescription` reads "Add {title} to favorites" / "Remove {title} from favorites" per the brief §26.

- [x] **Step 6: Verify** — `./gradlew test assembleDebug`.

- [x] **Checkpoint** — `feat: add movie detail screen`

---

## Task 14: Favorites — ViewModel and screen

**Files:**
- Create: `presentation/favorites/{FavoritesViewModel,FavoritesScreen}.kt`
- Test: `test/.../presentation/favorites/FavoritesViewModelTest.kt`

- [x] **Step 1: Write the failing test** — favorites emit in `addedAt DESC` order; removing one re-emits without it; the empty list yields the empty state.

- [x] **Step 2: Run to verify it fails.**

- [x] **Step 3: Implement the ViewModel and screen** — grid with stable keys, unfavorite affordance with an animated icon transition, empty state "Your favorite movies will appear here". Works with no network by construction.

- [x] **Step 4: Run tests to verify they pass.**

- [x] **Checkpoint** — `feat: add favorites screen`

---

## Task 15: Critical-journey Compose test (Robolectric)

**Files:**
- Create: `test/.../CriticalJourneyTest.kt`
- Create: `androidTest/.../FavoriteJourneyInstrumentedTest.kt`

- [x] **Step 1: Write the journey test** — search → result appears → open detail → tap favorite → navigate to Favorites → assert the movie is present. Backed by fake repositories, no network.

- [x] **Step 2: Add two supporting Compose tests** — the error state's retry button actually re-invokes the ViewModel; the empty state renders the exact copy from spec §12.4.

- [x] **Step 3: Run** — `./gradlew test`. Expected: PASS.

- [x] **Step 4: Write the instrumented variant** — committed for completeness. **It will not be executed in this environment**; the README must say so plainly rather than implying it passed.

- [x] **Checkpoint** — `test: add critical journey and UI state tests`

---

## Task 16: Accessibility and performance pass

- [x] **Step 1: Audit content descriptions** — every icon-only control is labelled; decorative images pass `null`; poster descriptions name the movie. No description reads "image" or "icon".
- [x] **Step 2: Audit touch targets** — every interactive element is ≥ 48.dp; add `Modifier.minimumInteractiveComponentSize()` where a control is visually smaller.
- [x] **Step 3: Audit font scaling** — no fixed-height text containers that clip at 200% font scale.
- [x] **Step 4: Audit recomposition** — lambdas passed to list items are stable; state is hoisted correctly; `derivedStateOf` guards scroll-driven computation; every lazy list has a stable `key`.
- [x] **Step 5: Verify** — `./gradlew test assembleDebug`.
- [x] **Checkpoint** — `refactor: accessibility and recomposition pass`

---

## Task 17: Documentation

**Files:**
- Create: `README.md`, `docs/screenshots/.gitkeep`

- [x] **Step 1: Write the README** — every section from brief §31, plus **Deliberate Non-Features** (spec §16) with all four entries.
- [x] **Step 2: Document setup honestly** — `OMDB_API_KEY` in `local.properties`; state plainly that `BuildConfig` keeps the key out of version control but does **not** make it secret, since it is recoverable from the APK, and that a backend proxy is the real answer.
- [x] **Step 3: Add screenshot placeholders** — five slots per brief §32 with capture instructions.
- [x] **Step 4: State test status accurately** — which suites were executed here and which were not.
- [x] **Checkpoint** — `docs: add project README and architecture documentation`

---

## Task 18: Senior audit and release verification

- [x] **Step 1: Run the full audit checklist** from brief §37 across architecture, Compose, coroutines, networking, Room, offline, UX, accessibility, security, testing, and repository hygiene.
- [x] **Step 2: Fix every finding** — the brief requires fixing, not merely reporting.
- [x] **Step 3: Verify no secrets are tracked** — `git log -p --all -- local.properties` must be empty; grep the tree for a literal key.
- [x] **Step 4: Remove dead code** — unused dependencies, debug logs, leftover TODOs, unused strings.
- [x] **Step 5: Final verification** — Run: `./gradlew clean test assembleDebug assembleRelease`. Expected: all green, and `app/build/outputs/apk/release/` contains a signed, installable APK.
- [x] **Step 6: Report honestly** — including that the minified release APK has not been runtime-smoke-tested on a device, since no emulator was run.
- [x] **Checkpoint** — `chore: final audit fixes and release verification`

---

## Self-Review

**Spec coverage:** §4 architecture → Tasks 2–14. §5 packages → file structure table. §6 models/mappers → Task 3. §6.3 entities → Task 4. §7 caching + `Cached<T>` → Tasks 2, 6. §8 request minimisation → Tasks 1, 3, 6, 10. §9 errors → Tasks 2, 3, 8. §10 favorite consistency → Tasks 5, 7. §11 use cases → Task 7. §12 presentation → Tasks 8–14. §13 search + §13.1 → Task 10. §14 testing → Tasks 3–7, 10, 12–15. §15 build → Tasks 1, 18. §16 non-features → Task 17. §19 acceptance → Task 18.

**Type consistency:** `MovieListItem`, `CollectionRow`, `HomeFeed` defined in Task 7 before use in Tasks 12–14. `Cached<T>`/`RefreshState` defined in Task 2, consumed in 6/12/13. `AppResult`/`AppError` defined in Task 2, used from Task 3 on. `observeFavoriteIds(): Flow<Set<String>>` is consistent across Tasks 5, 7, 10.

**Known gap accepted:** exact dependency versions are deliberately unpinned in this plan — Task 1 Step 5 resolves them empirically against the toolchain, because guessing them here would be a placeholder in disguise.
