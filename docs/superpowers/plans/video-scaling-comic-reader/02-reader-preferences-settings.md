# TODO 02 Reader Preferences and Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增独立、立即保存的默认看图方式设置，同时保持服务器 URL 必须探测成功后才能保存的原逻辑。

**Architecture:** `ReaderPreferencesRepository` 使用独立 Preferences DataStore 和稳定的 `ImageReaderMode` 枚举。`SettingsViewModel` 同时读取服务器配置与阅读偏好，但两者的写入状态、错误和按钮互不耦合。

**Tech Stack:** Kotlin、AndroidX Preferences DataStore 1.2.1、Compose Material 3、Coroutines、JUnit/Robolectric、Compose UI Test。

## Global Constraints

- 默认值固定为 `ImageReaderMode.COMIC`。
- 可保存值只有 `COMIC` 和 `SINGLE`。
- 选择阅读方式后立即保存。
- 阅读偏好保存不调用服务器探测或 `saveCandidate()`。
- 阅读偏好失败不改变服务器 URL 输入及其 `canSave`。
- 不向 `ServerConfig` 增加 UI 偏好字段。

## Files

- Create: `app/src/main/java/com/local/mediaviewer/image/ImageReaderMode.kt`
- Create: `app/src/main/java/com/local/mediaviewer/image/ReaderPreferencesRepository.kt`
- Create: `app/src/main/java/com/local/mediaviewer/image/DataStoreReaderPreferencesRepository.kt`
- Create: `app/src/test/java/com/local/mediaviewer/image/DataStoreReaderPreferencesRepositoryTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/settings/SettingsViewModelTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/settings/SettingsScreen.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/AppContainer.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/testing/FakeAppContainer.kt`

## Interfaces

- Produces:

```kotlin
enum class ImageReaderMode {
    COMIC,
    SINGLE,
}

interface ReaderPreferencesRepository {
    val defaultMode: Flow<ImageReaderMode>
    suspend fun currentDefaultMode(): ImageReaderMode
    suspend fun setDefaultMode(mode: ImageReaderMode)
}

class DataStoreReaderPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : ReaderPreferencesRepository
```

- Updated settings constructor:

```kotlin
class SettingsViewModel(
    private val settings: ServerSettingsRepository,
    private val readerPreferences:
        ReaderPreferencesRepository,
    private val session: ServerSessionManager,
) : ViewModel
```

- Updated UI state and event:

```kotlin
data class SettingsUiState(
    // Existing server fields remain.
    val defaultImageMode:
        ImageReaderMode = ImageReaderMode.COMIC,
    val isSavingImageMode: Boolean = false,
    val imageModeError: String? = null,
)

fun onDefaultImageModeChanged(mode: ImageReaderMode)
```

## Steps

- [ ] **Step 1: Write failing DataStore preference tests**

Create `DataStoreReaderPreferencesRepositoryTest.kt`:

```kotlin
@Test
fun `缺少键时默认条漫并可保存单图`() = runTest {
    val store = InMemoryPreferencesDataStore()
    val repository =
        DataStoreReaderPreferencesRepository(store)

    assertEquals(
        ImageReaderMode.COMIC,
        repository.currentDefaultMode(),
    )

    repository.setDefaultMode(ImageReaderMode.SINGLE)

    assertEquals(
        ImageReaderMode.SINGLE,
        repository.currentDefaultMode(),
    )
}
```

Add a malformed-value test by writing `"unsupported"` directly to the
preferences key and asserting fallback to `COMIC`.

- [ ] **Step 2: Run the preference test and verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.DataStoreReaderPreferencesRepositoryTest'
```

Expected: compilation fails because the new mode and repository do not exist.

- [ ] **Step 3: Implement mode and DataStore repository**

`ImageReaderMode.kt`:

```kotlin
package com.local.mediaviewer.image

enum class ImageReaderMode {
    COMIC,
    SINGLE,
}
```

`ReaderPreferencesRepository.kt` contains only the interface.

`DataStoreReaderPreferencesRepository.kt`:

```kotlin
val Context.readerPreferencesDataStore by preferencesDataStore(
    name = "reader_preferences",
)

class DataStoreReaderPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : ReaderPreferencesRepository {
    private object Keys {
        val defaultMode =
            stringPreferencesKey("default_image_reader_mode")
    }

    override val defaultMode: Flow<ImageReaderMode> =
        dataStore.data.map { preferences ->
            preferences[Keys.defaultMode]
                ?.let { saved ->
                    ImageReaderMode.entries.firstOrNull {
                        it.name == saved
                    }
                }
                ?: ImageReaderMode.COMIC
        }

    override suspend fun currentDefaultMode(): ImageReaderMode =
        defaultMode.first()

    override suspend fun setDefaultMode(
        mode: ImageReaderMode,
    ) {
        dataStore.edit { preferences ->
            preferences[Keys.defaultMode] = mode.name
        }
    }
}
```

- [ ] **Step 4: Run the DataStore tests**

Run the Step 2 command.

Expected: both tests pass.

- [ ] **Step 5: Write failing SettingsViewModel tests**

Extend the test fake with `FakeReaderPreferencesRepository` and add:

```kotlin
@Test
fun `阅读方式立即独立保存且不调用服务器保存`() =
    runTest(dispatcher) {
        val reader = FakeReaderPreferencesRepository(
            ImageReaderMode.COMIC,
        )
        val session = SettingsFakeSession {
            error("不应探测服务器")
        }
        val viewModel = SettingsViewModel(
            SettingsFakeRepository(ServerConfig()),
            reader,
            session,
        )
        advanceUntilIdle()

        viewModel.onDefaultImageModeChanged(
            ImageReaderMode.SINGLE,
        )
        advanceUntilIdle()

        assertEquals(
            ImageReaderMode.SINGLE,
            reader.currentDefaultMode(),
        )
        assertEquals(0, session.saveCalls)
        assertFalse(viewModel.uiState.value.canSave)
    }
```

Add a test asserting initial saved `SINGLE` is exposed without changing the
server URL input.

- [ ] **Step 6: Implement settings state and save event**

Load server and reader values in separate `viewModelScope.launch` blocks so
one slow source cannot block the other.

Implement:

```kotlin
fun onDefaultImageModeChanged(mode: ImageReaderMode) {
    if (
        mode == mutableUiState.value.defaultImageMode ||
        mutableUiState.value.isSavingImageMode
    ) {
        return
    }
    val previous = mutableUiState.value.defaultImageMode
    mutableUiState.value = mutableUiState.value.copy(
        defaultImageMode = mode,
        isSavingImageMode = true,
        imageModeError = null,
    )
    viewModelScope.launch {
        runCatching {
            readerPreferences.setDefaultMode(mode)
        }.onSuccess {
            mutableUiState.value = mutableUiState.value.copy(
                isSavingImageMode = false,
            )
        }.onFailure {
            mutableUiState.value = mutableUiState.value.copy(
                defaultImageMode = previous,
                isSavingImageMode = false,
                imageModeError = "默认看图方式保存失败",
            )
        }
    }
}
```

Do not change `testConnection()` or the server `save()` method.

- [ ] **Step 7: Add the settings UI section**

Below the server save button add:

```kotlin
Text(
    text = "图片阅读",
    style = MaterialTheme.typography.titleMedium,
)
Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    FilterChip(
        selected =
            state.defaultImageMode == ImageReaderMode.COMIC,
        onClick = {
            onDefaultImageModeChanged(ImageReaderMode.COMIC)
        },
        label = { Text("条漫") },
        enabled = !state.isSavingImageMode,
        modifier = Modifier.testTag("default_reader_comic"),
    )
    FilterChip(
        selected =
            state.defaultImageMode == ImageReaderMode.SINGLE,
        onClick = {
            onDefaultImageModeChanged(ImageReaderMode.SINGLE)
        },
        label = { Text("单图") },
        enabled = !state.isSavingImageMode,
        modifier = Modifier.testTag("default_reader_single"),
    )
}
```

Expose `onDefaultImageModeChanged` from `SettingsScreen` and wire it from
`MediaViewerApp`.

- [ ] **Step 8: Update container wiring and strict fakes**

Add to `AppContainer`:

```kotlin
val readerPreferencesRepository:
    ReaderPreferencesRepository
```

In `DefaultAppContainer`:

```kotlin
override val readerPreferencesRepository =
    DataStoreReaderPreferencesRepository(
        appContext.readerPreferencesDataStore,
    )
```

`FakeAppContainer` uses an in-memory implementation initialized to
`ImageReaderMode.COMIC`.

- [ ] **Step 9: Add Compose settings tests**

Update `HomeSettingsScreenTest` to assert:

```kotlin
rule.onNodeWithText("图片阅读").assertIsDisplayed()
rule.onNodeWithTag("default_reader_comic")
    .assertIsSelected()
rule.onNodeWithTag("default_reader_single")
    .performClick()
```

After the click, assert the callback received `SINGLE` while
`save_server` remains disabled unless its existing probe succeeds.

- [ ] **Step 10: Run focused and regression tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.DataStoreReaderPreferencesRepositoryTest' `
  --tests 'com.local.mediaviewer.settings.SettingsViewModelTest'
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.HomeSettingsScreenTest'
```

Expected: all tests pass; the existing server validation tests remain green.

- [ ] **Step 11: Review and commit**

Run:

```powershell
git diff --check
git status --short
git diff -- app/src/main app/src/test app/src/androidTest
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: persist default image reader mode"
```

Confirm no image list or video UI implementation is included.
