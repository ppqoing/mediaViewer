# Final player integration review fixes

Base: `ce3c439eb1f66b69a12198a0e2376b33b097fcbb`

## TDD evidence

### Group A — current item UI and notification navigation

RED:

- `PlayerViewModelTest`: mixed audio/video A -> B and removal of the old routed
  item did not update the page name, kind, and current media key.
- `CurrentPlayerNavigationTest`: the current-player request tracker, route
  loading/empty decision, and notification intent contract did not exist.

GREEN:

- `PlayerRoute` is driven by `sessionState.currentItem`, not the route media key.
- The view model follows the session item's name, kind, and media key.
- Initial empty controller state waits; an empty queue exits only after a player
  item has actually been presented.
- Notification requests survive the initial empty connection window and are
  consumed once when the current item is available.
- `MainActivity` accepts both cold intents and `onNewIntent`; the notification
  uses an explicit action/extra and `singleTop`.

### Group B — controller/service lifecycle and terminal queue behavior

RED:

- `ControllerConnectionMachineTest`: paused disconnect always reconnected;
  there was no dormant/demand state or paused-vs-playing app-stop behavior.
- `PlaybackCoordinatorTest`: sequential and shuffle queue terminals stopped the
  engine without clearing `playWhenReady`.
- `PlaybackReleaseSequenceTest`: explicit STOP did not prove that playback
  intent was cleared before resources were released.

GREEN:

- Paused disconnect/app stop enters `Dormant`; the next app start or user
  command reconnects. A playing unexpected disconnect still reconnects.
- A failed connection attempt also stops retrying when the last playback intent
  is paused; a later command creates the next connection generation.
- App `ON_STOP` only releases a paused MediaController connection and never
  pauses/stops the engine; `ON_START` restores demand.
- Explicit service STOP saves first, clears playback intent, then releases.
- Sequential and shuffle terminals clear playback intent and stop the engine.

### Group C — snapshot ownership and background endpoint recovery

RED:

- Coordinator tests failed for immediate pause persistence, A -> B ownership,
  ended A persistence, background refresh/reload, one-attempt error storms,
  retry reset after successful playback, and Chinese refresh failure output.
- Queue-backed `PlayerViewModel` still duplicated endpoint recovery.

GREEN:

- `setQueue` persists the old current item while holding the coordinator mutex,
  before changing the current media key. Pause/stop/session pause save
  immediately through a private locked helper.
- Ended snapshots retain `ended=true`; old engine positions are not recorded
  against the newly selected item.
- Background errors refresh/reload in `PlaybackCoordinator`, preserving the
  queue, current item, position, and play intent without auto-skipping.
- Recovery is limited once per current item/error storm and resets after
  successful `PLAYING` or a current-item change.
- Queue-backed view models no longer perform a duplicate endpoint refresh.

## Verification

Focused RED/GREEN suites:

- `PlayerViewModelTest`
- `CurrentPlayerNavigationTest`
- `ControllerConnectionMachineTest`
- `PlaybackCoordinatorTest`
- `PlaybackReleaseSequenceTest`
- `VlcSessionPlayerTest`

Final local gate (fresh rerun):

```text
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease compileDebugAndroidTestKotlin
BUILD SUCCESSFUL
241 JVM tests, 0 failures
```

The first full-gate run exposed three `VlcSessionPlayerTest` failures caused by
publishing a synthetic `OPENING` state instead of respecting the synchronous
`PlaybackEngine.prepare()` state boundary. The implementation now reads
`engine.state.value` after prepare, the fake engine mirrors that contract, and
endpoint-refresh exceptions are converted to a Chinese playback error. The
focused suites and the complete gate were rerun after that correction.

## NOT RUN

- Connected-device / emulator instrumentation execution.
- Physical-device notification tap, task removal, audio-focus, noisy-output,
  and background playback observation.
- Real media server smoke tests.

The Android instrumentation sources were compiled by
`compileDebugAndroidTestKotlin`.
