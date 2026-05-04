# Sub-plans for the Unified Study Wizard + Exam mode

The canonical spec is [`../plan.md`](../plan.md). These files chunk it into agent-sized hand-offs.

Each sub-plan is **self-contained**: the prompt to give a coding agent is "Read `plan.md` and `plans/XX-*.md`; implement the sub-plan. Conform to the schemas/routes in `plan.md`. Ask before deviating."

Land them in order — each one assumes the prior commit point is in place.

| # | File | Phase | Roughly |
|---|------|-------|---------|
| A  | [A-rename-test-to-quiz.md](A-rename-test-to-quiz.md)            | A  | Mechanical rename, no behavior change |
| B1 | [B1-study-controller-skeleton.md](B1-study-controller-skeleton.md) | B  | `StudyController`, `StudyMode`, mode picker |
| B2 | [B2-wizard-fragments-and-js.md](B2-wizard-fragments-and-js.md)   | B  | Extract panels + orchestrator JS |
| B3 | [B3-entry-point-migration.md](B3-entry-point-migration.md)       | B  | Re-point sidebar/explorer/folder/deck/file links |
| C1 | [C1-exam-backend.md](C1-exam-backend.md)                         | C  | Entities, DTOs, services, controller (no UI) |
| C2 | [C2-wizard-exam-panel.md](C2-wizard-exam-panel.md)               | C  | Wizard exam config panel + setup integration |
| C3 | [C3-exam-runtime-ui.md](C3-exam-runtime-ui.md)                   | C  | Exam run/grading/result templates + JS + CSS |
| D  | [D-past-exams.md](D-past-exams.md)                               | D  | List + detail + rename + delete |

Phase E (polish) is intentionally not split — it's a manual visual-QA pass after D lands.
