# Review Card Mobile Layout Design

## Goal

Reduce the vertical space used by the dashboard "Due today" review card on narrow screens.

## Approved Layout

On mobile, the icon should sit in front of the "Due today" title and "Cards scheduled for review" text. The review count badge remains beside the title. The "Study now" call to action remains a full-width row below the header copy so it stays easy to tap.

Desktop layout stays unchanged.

## Implementation

Update the review card markup with a small wrapper around the icon and text content. Use existing review card classes where possible, and add a focused mobile CSS rule at the current `max-width: 480px` breakpoint.

## Verification

Run the UI resource regression test that covers the review card fragment and CSS.
