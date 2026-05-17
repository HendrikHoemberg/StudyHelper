# PDF Splitter Page Zoom Design

## Goal

Let users increase or decrease the visible size of PDF splitter page cards when they need either a broader overview for many pages or a closer look at thumbnails.

## User Experience

The splitter header includes compact zoom controls next to the title and close button:

- decrease size
- current zoom percentage / reset to default
- increase size

The default is `100%`. The controls use bounded steps from `60%` to `160%` in `20%` increments. Opening a PDF resets zoom to `100%`.

## Implementation

The page grid uses a CSS variable for the minimum page card width. JavaScript updates the variable on `#sh-ps-pages` when zoom changes. The existing rendered PDF canvases are not re-rendered; the browser scales the cards. This keeps zoom changes immediate and avoids expensive PDF.js work for large documents.

## Testing

Update the existing UI resource regression test to assert that the splitter fragment, CSS, and JavaScript include the zoom controls, CSS variable, and zoom state.
