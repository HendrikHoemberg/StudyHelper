# Comprehensive Flashcard Generation With Predictable Quota Cost

**Date:** 2026-05-24
**Status:** Approved (design)

## Problem

The flashcard generator currently asks the user to choose a card count before generation. That is not a useful decision for the user: they do not know how many cards a PDF needs, and the model gives inconsistent results when asked to choose a global count from whole PDFs.

For the desired product behavior, the user should select PDFs and receive flashcards for every testable detail. The generated card count should emerge from the material, while the AI request cost remains clear before generation starts.

## Goals

- Remove the user-facing card-count requirement from flashcard generation.
- Generate comprehensive flashcards by covering every testable detail in the selected PDFs.
- Make the quota cost predictable before generation starts.
- Warn users when large PDFs are likely to consume significant quota, take longer, or produce weaker results.
- Keep Gemini Flash Lite viable by turning one large global task into smaller bounded tasks.
- Avoid long-running browser-held HTTP requests for generation.

## Non-goals

- No hard per-generation cap on AI requests.
- No switch to a different model as part of the first implementation.
- No changes to quiz or exam generation.
- No perfect semantic deduplication guarantee in the first version.

## User Experience

The flashcard generator remains a PDF-to-deck workflow:

1. User selects one or more PDFs.
2. The app locally estimates the generation plan.
3. The UI shows estimated AI requests, estimated time, and expected output size.
4. If the plan is large, the UI shows a warning banner about quota, generation time, and result-quality risks.
5. User starts generation.
6. The submit request returns quickly with a generation progress view.
7. The app polls generation status while the server runs the work in the background.
8. When complete, the UI redirects or swaps to the saved deck.
9. The result page reports how many cards were generated and how many AI requests were used.

The generator should explain the behavior as:

> StudyHelper creates one flashcard for each testable detail it finds.

The UI should avoid saying the AI "decides the number of cards" as the primary mental model. The system is coverage-driven.

## Timeout Requirements

Long generations must not depend on one open browser HTTP request staying alive until every chunk is done.

The implementation must use a short request/response lifecycle:

1. User submits generation.
2. Server validates the plan and quota.
3. Server records the generation job.
4. Server starts background processing.
5. Server immediately returns a progress view.
6. Client polls a status endpoint until the job is complete or failed.

This removes the main timeout risk from:

- browser-held HTMX requests;
- reverse-proxy response-header waits;
- mobile tab sleep or network interruption;
- request lifecycle limits in servlet infrastructure;
- provider calls that are individually fine but collectively take many minutes.

### Current Timeout Audit

The current codebase does not configure a short timeout for flashcard generation, but relying on that is still not enough for long jobs.

- `src/main/resources/application.properties` has no `server.tomcat.*`, `spring.mvc.async.request-timeout`, or generation-specific timeout.
- `Caddyfile` uses `reverse_proxy app:8080` with no response timeout configured. Caddy's `reverse_proxy` HTTP transport defaults to no `response_header_timeout`, no `read_timeout`, and no `write_timeout`.
- HTMX is loaded from `https://unpkg.com/htmx.org@2.0.4` and the app does not set `htmx.config.timeout`. HTMX's default request timeout is `0`.
- Spring AI's Google GenAI auto-configuration builds `com.google.genai.Client` without custom HTTP options.
- The resolved Google GenAI Java SDK creates an OkHttp client with connect/read/write timeouts set to `0ms` unless `HttpOptions.timeout` is provided. That avoids provider read-timeout failures for long individual calls, but it also means a stuck provider call can hang indefinitely.

The background-job design addresses request timeout fragility. Individual provider calls should additionally be bounded by the app at the job level, not by an HTTP request timeout.

### Provider-Call Timeout

Because each provider call handles only one small chunk, configure an explicit high finite timeout for individual Gemini calls:

```text
provider call timeout = 180 seconds per chunk request
```

This timeout is not a cap on the whole generation. A 40-chunk generation may take far longer than 180 seconds overall because each chunk is processed as a separate request in the background job.

The implementation should provide its own `com.google.genai.Client` bean configured with `HttpOptions.timeout(180000)` so Spring AI uses the same Google GenAI client without relying on the SDK's no-timeout default.

### Job-Level Timeout

Use an application-level job timeout instead of a browser/proxy request timeout:

```text
max job runtime = max(10 minutes, estimated upper time * 2, chunk count * provider call timeout + 2 minutes)
```

If a job exceeds this runtime, mark it failed with a clear message and do not save a partial deck.

This timeout protects server resources without making normal long generations fail because a browser, proxy, or HTMX request was held open too long.

## Chunking Rule

Use deterministic local chunking:

```text
1 AI request = up to 2 PDF pages or 1,500 extracted words, whichever boundary comes first.
```

Text-mode PDFs use extracted text, page order, and word counts. Full-PDF mode uses page-based PDF chunks created locally with PDFBox so each provider request receives only the planned page range.

## Generation Pipeline

### Standard Comprehensive Mode

This is the first mode to ship.

For each chunk, make one AI request that asks Gemini to:

- identify every testable detail in the chunk;
- create atomic flashcards from those details;
- skip metadata, headers, footers, page numbers, bibliographies, acknowledgements, and layout-only details;
- keep cards self-contained;
- preserve the dominant source language;
- return structured JSON matching the existing generated flashcard schema.

The final deck is the concatenation of all valid chunk outputs, followed by local cleanup.

The pipeline runs inside the background job worker. Each chunk completion updates job progress.

### Local Cleanup

After all chunks complete:

- discard invalid cards with blank fronts or backs;
- normalize whitespace;
- remove exact duplicate front/back pairs;
- remove very near duplicates with conservative local string similarity.

The first version should prefer conservative dedupe over aggressive merging, because dropping a true detail is worse than keeping a minor duplicate for this feature.

### Future Verified Mode

Verified mode is a later enhancement, not required for the first implementation.

It would add a second AI request per chunk that receives the chunk plus the generated cards for that chunk and asks which testable details are missing. Any missing details would be turned into additional cards.

Quota rule:

```text
Verified cost = chunk count * 2
```

## Quota Model

The current `AiRequestQuotaService#checkAndRecord(User)` records one request per generation. Chunked generation needs an amount-based quota operation.

Add a quota path equivalent to:

```java
checkAndRecord(User user, int requestCount)
```

For the first implementation, charge exactly the preflighted cost when the generation job is accepted. This keeps the displayed cost and charged cost identical. If an AI request fails after quota is charged, the app should preserve today's behavior: the quota was consumed because the provider was reached.

The design intentionally avoids refunds in the first version. Refund logic would make quota behavior harder to reason about and would require distinguishing provider failures, validation failures, and local failures more carefully.

## Preflight Plan

Introduce a local generation plan before submit:

```text
selected PDFs
document mode
page count
extracted word count for text-mode generation
chunk count
estimated request cost
estimated generation time
estimated card range
risk level
```

The preflight should not call the AI provider and should not consume quota.

The controller should reject generation if the user does not have enough remaining daily AI requests for the estimated cost.

The accepted job stores the plan snapshot used for charging so the displayed cost, charged cost, and job execution are consistent.

## Background Job Model

Introduce persisted generation jobs so progress survives page navigation and the UI does not depend on one long request.

Suggested job fields:

```text
id
user
status: QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED
destination configuration
selected source file ids
document mode
additional instructions
estimated request cost
charged request cost
chunk count
completed chunk count
generated card count
failure message
created at
started at
finished at
```

Execution rules:

- A generation POST validates inputs, builds the preflight plan, checks quota, records quota, creates the job, and returns a progress fragment.
- A worker processes the job outside the servlet request thread.
- The worker generates all chunks, deduplicates results, then saves the deck in one final persistence step.
- If any chunk fails, the job fails and no deck is saved.
- The UI polls job status with a short HTMX request.
- On success, the status response points the UI to the saved deck.
- On failure, the status response shows the error and refreshes quota.

Cancellation is not part of the first implementation.

## Warning Banner

Show progressively stronger warnings based on the estimated request cost.

Suggested thresholds:

```text
Normal:      1-10 AI requests
Warning:     11-30 AI requests
High risk:   31+ AI requests
```

Normal runs can show a compact estimate. Warning and high-risk runs should show a banner that explains:

- estimated AI request cost;
- estimated generation time;
- large PDFs can produce duplicate or uneven cards;
- for best results, split large PDFs by lecture, chapter, or topic.

High-risk runs should require explicit acknowledgement before submit. They are not blocked.

The warning should fit the existing generator UI and reuse the app's alert/banner styling where possible.

## Time Estimate

Use a simple deterministic estimate:

```text
estimated seconds = chunk count * configured seconds per chunk
```

The initial default can be 8-12 seconds per chunk and should be expressed as a range in the UI. The exact value is a product estimate, not a guarantee.

## Card Estimate

The app cannot know the exact card count before AI generation. It can show a broad estimate based on chunk count:

```text
estimated cards = chunk count * 5-15
```

This should be labeled as approximate. The exact saved-card count is shown after generation.

## Prompt Direction

The chunk prompt should define "testable detail" clearly:

- definition;
- fact;
- formula;
- rule;
- process step;
- comparison;
- cause/effect relationship;
- named concept;
- exception or caveat;
- example that teaches a concept.

It should also define what to skip:

- page numbers;
- document metadata;
- headers and footers;
- references and bibliographies;
- acknowledgements;
- decorative or layout-only content;
- vague references to images unless the needed information is present in text.

The prompt should not request a fixed card count. It should request all testable details from the given chunk.

## Controller and Service Shape

Expected new or changed components:

- `FlashcardGenerationJob`
  - persisted record for queued/running/completed generation work.
- `FlashcardGenerationJobRepository`
  - stores and loads jobs for the current user.
- `FlashcardGenerationJobService`
  - accepts jobs, starts worker execution, exposes status, and enforces job-level timeout.
- `GoogleGenAiClientConfig`
  - provides a Google GenAI `Client` bean with a high finite per-call timeout.
- `FlashcardGenerationPlanService`
  - builds the local preflight plan from selected `DocumentInput`s;
  - owns chunking and request-cost estimation.
- `FlashcardChunk`
  - represents one generation unit with source filename, chunk index, page range if known, and text content.
- `AiFlashcardService`
  - gains a chunk-generation path;
  - generates cards for each chunk and locally deduplicates the aggregate result.
- `AiRequestQuotaService`
  - gains an amount-based check-and-record method.
- `FlashcardGenerationController`
  - preflights selected PDFs;
  - records the estimated request cost;
  - creates generation jobs;
  - exposes a status endpoint for polling;
  - preserves current error handling and destination persistence.

## UI Changes

- Remove the card-count stepper from the flashcard generator.
- Add a generation estimate area in the destination step:
  - estimated AI requests;
  - estimated time;
  - approximate card range;
  - risk banner when applicable.
- Add high-risk acknowledgement for large runs.
- Replace the long-running submit response with a progress view that polls job status.
- Keep the additional-instructions flow, but make it clear that instructions refine coverage or focus rather than setting the normal card count.

## Error Handling

- Validation errors before provider calls should not consume quota.
- If the user lacks enough remaining AI quota for the estimated cost, show a clear error before creating a job.
- If a provider request fails mid-run, mark the job failed, show the existing AI-generation error pattern, and refresh quota.
- If some chunks succeed and a later chunk fails, the first version should fail the whole generation rather than saving a partial deck. Partial-save/resume can be a later enhancement.
- If a job exceeds the job-level timeout, mark it failed and show a timeout-specific message.

## Testing

Service tests:

- chunk count follows the 2-page / 1,500-word rule;
- preflight computes request cost without provider calls;
- amount-based quota rejects when remaining quota is insufficient;
- accepted jobs store the charged request cost from the preflight plan;
- job execution runs outside the controller request path;
- job-level timeout marks stale running jobs failed;
- Google GenAI client configuration applies the explicit provider-call timeout;
- chunk prompt asks for every testable detail and does not include a fixed card count;
- generated cards from multiple chunks are aggregated;
- exact duplicate cards are removed.

Controller tests:

- POST without card count uses the chunked path;
- generation creates a job and returns a progress fragment instead of waiting for all chunks;
- insufficient quota returns the generator with an error and does not call Gemini;
- high-risk acknowledgement is required for high-risk plans;
- status endpoint returns progress, success, and failure states;
- validation errors still do not consume quota.

UI regression tests:

- card-count stepper is absent;
- estimate fields are present;
- warning banner copy is present;
- high-risk acknowledgement markup is present.
- progress polling markup is present.

## Risks

- Background jobs add implementation complexity and likely require a new persisted job table.
- The app needs both a finite provider-call timeout and a job-level timeout to avoid jobs running forever.
- Chunk-level generation may create style differences between chunks. Prompt wording and local cleanup should reduce this, but it will not disappear completely.
- Context that spans chunks may produce weaker cards. Page/word chunk sizes are a pragmatic tradeoff for quota predictability.
- Full-PDF visual chunking depends on reliable PDFBox page extraction for chunk PDFs.
