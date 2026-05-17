# PDF Splitter Selective Parts Design

## Goal

Allow users to split a PDF into multiple ranges and choose which resulting parts are saved. Deselected ranges are discarded and no file entry is created for them.

## Current Behavior

The PDF splitter renders page thumbnails, lets the user place split points, and lists every resulting part in the sidebar with a filename input. Saving sends every computed range to `POST /files/{id}/split`. `PdfSplitService` currently requires submitted ranges to cover every page of the source PDF without gaps.

## User Experience

Each part row in the sidebar gets a keep checkbox. All parts are selected by default. Deselecting a part dims the row, disables its filename input, and removes that part from the save count.

The footer button reflects the number of selected parts, for example `Save 2 parts`. The button remains disabled until the PDF has been split into at least two ranges and at least one range is selected for saving.

When the user saves, only selected parts are submitted. For example, if the split creates A `1-3`, B `4-7`, and C `8-10`, and the user deselects B, the request contains only A and C. Pages `4-7` are not saved anywhere.

## Backend Behavior

`SplitPdfRequest` can continue to contain only `parts`; no separate discard list or `keep` flag is needed.

`PdfSplitService` validation changes from requiring complete document coverage to accepting any non-empty list of valid requested ranges. The submitted parts must:

- have non-blank names
- have `startPage >= 1`
- have `endPage >= startPage`
- have `endPage <= pageCount`
- be sorted and non-overlapping

Non-contiguous gaps are allowed because gaps represent discarded split parts. Existing full-save behavior remains unchanged because the frontend starts with all parts selected.

## Error Handling

The frontend should prevent empty submissions by disabling the save button when no parts are selected. The backend still rejects an empty or null `parts` list to protect the endpoint.

Invalid ranges continue to return a bad request through the existing controller path.

## Testing

Add service tests proving that non-contiguous ranges are accepted and overlapping ranges are rejected. Update UI resource regression coverage to assert that the splitter contains keep-selection controls and selected-count logic.
