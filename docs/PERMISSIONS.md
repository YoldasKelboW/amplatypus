# Permissions from source operators

This file records the permissions the app relies on. A source that needs permission stays disabled in
release builds until its entry here is filled in.

| Source | Operator | Status | Date |
|---|---|---|---|
| Modland | Coma (Daniel Johansson) | public archive; to be informed | – |
| Amiga Music Preservation | AMP staff | **granted** – API use without key, conditions below | 2026-11 |
| 4champ.net API | Aleksi Sitomaniemi | **not available** – restricted to the 4champ app | 2026-11 |

When a permission arrives, note the date and any conditions such as rate limits, keys or a preferred
User-Agent. Private correspondence is not quoted or linked here.

## Amiga Music Preservation

The AMP staff allowed this app to use the AMP API (https://amp.dascene.net/api) in November 2026.
The server recognises the app by its User-Agent, so no key is built into the app.

Conditions, and where the app keeps them:

- **Identifying User-Agent** of the form `AMPlatypus/<version> (Android; +<repository URL>)`.
  `net/ApiConfig.kt`; `ApiConfigTest` checks it against the pattern the server expects.
- **At most 5 requests per 10 seconds.** `net/RateLimiter.kt`, used by `source/amp/AmpSource.kt`.
- **No hammering, no bulk downloads.** Searches wait until typing stops, answers are cached for 30 minutes,
  the radio fetches one module at a time.
- **get_similar_modules with at most 5 results** (it is costly for the server), and the "sounds alike"
  radio remembers recently played modules so it does not loop. `Endpoints.AMP_SIMILAR_LIMIT`.
- Calls used: search_modules_paged, search_by_handle, get_modules_by_author, get_module_details
  (with and without index), get_latest_additions, get_similar_modules; files from `/module/<id>`.
- AMP is credited in the app (search screen) and in the README and NOTICE. The app stays non-profit:
  no ads, no tracking, no payments.
