# AI Horde image rendering

Chronosphere currently uses AI Horde as the only remote image provider.

## Runtime flow

1. `OfflineSceneView` creates a deterministic Horde request from the existing `ResolvedScene`.
2. The old local renderer is displayed immediately while the remote job is queued.
3. `HordeClient` submits to `/api/v2/generate/async`, polls `/generate/check/{id}`, and retrieves `/generate/status/{id}` only after completion.
4. The returned image is copied immediately into the app-private `filesDir/horde-images` cache.
5. If the request times out, fails, is censored, or returns invalid bytes, the local renderer remains visible and the UI offers a retry.

## Provider and credentials

The first implementation intentionally uses the AI Horde anonymous API key `0000000000`. This keeps setup at zero cost but gives the request the lowest queue priority. No paid fallback or second provider exists in this phase.

Anonymous AI Horde image requests are public/shared according to the current API contract even if `shared=false` is submitted. Do not send personal photos, secrets, identifying private data, or confidential prompts through this path.

## Adult-content guard

`UNDRESSED` scenes are sent with `nsfw=true` only when `ageYears >= 18`. The request model and prompt factory both enforce this before any network call. Minor prompts are explicitly SFW and add nudity/sexualization terms to the negative prompt.

## Model choice

The client queries `/status/models`, keeps a short cache of currently active model names, and intersects that list with preferred realistic models. If the status request fails or none of the preferred names are currently active, the `models` field is omitted so Horde can choose a compatible active worker.

## Identity stability

The first phase uses a stable text seed derived from the character key. This improves repeatability but does not guarantee face identity across different models or major prompt changes. A later phase should add an explicit sex/gender field to the character model and then use Horde img2img/reference-image support for stronger visual continuity.

## Build policy

The local renderer is not removed. Network loss, Horde capacity problems, and API changes must never prevent the simulation or character card from working.
