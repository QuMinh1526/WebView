# Mini Studio implementation plan

The current app already has a `PageScriptInjector` seam, per-origin microphone grants in
`SettingsRepository`, a centralized `BrowserWebViewPool`, and Compose browser controls.
The injector is currently a no-op and must remain opt-in, main-frame-only, and origin-scoped.

## Safety boundary

Mini Studio will not include the requested 200x gain or a preset designed to overwhelm
listeners. A digital `-1 dBFS` ceiling cannot guarantee safe acoustic loudness on every
headphone or speaker. Processing will never intentionally amplify above the clean mic
signal; the limiter and explicit microphone grant remain mandatory. No processing runs
when the feature is disabled, the origin is not approved, or microphone permission is denied.

## Steps

1. Add this plan, then build/test/lint and commit the documentation checkpoint.
2. Add normalized parameter models, safe scale mapping, preset serialization, origin
   validation, DataStore persistence, and focused unit tests; build/test/lint and commit.
3. Add the page-side audio processor and a document-start, exact-origin injector. Wrap only
   audio `getUserMedia`, preserve the raw stream when disabled, require the existing mic
   grant, use a mandatory output limiter, and avoid verification/challenge frames; build,
   test, lint, then commit.
4. Add Mini Studio controls, enabled-origin management, presets/import-export, panic/bypass,
   status chip, and a lightweight opt-in HUD. Keep sliders realtime and avoid page reloads;
   build, test, lint, then commit.
5. Document device test cases and limitations. Run the requested clean build/test/lint,
   review the staged diff, and report any device-only checks that could not be verified.

The parameter table will be the single JSON source used by Kotlin tests/settings and
embedded into the injected JavaScript. Integer slider positions map continuously and
monotonically; values are normalized for portable preset storage.
