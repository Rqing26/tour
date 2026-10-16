# Workspace UI implementation plan

**Goal:** Replace promotional UI with a clear Google Workspace inspired application interface, without changing business behavior.

**Architecture:** Keep React components and existing API calls. Update shared shell and reusable controls first, then page-specific presentation. Consolidate the visual system in CSS variables and responsive rules.

**Tech stack:** React 19, TypeScript, Vite, CSS, Lucide icons.

## 1. Sign in and shared shell

- Remove the demo role picker and password display from `frontend/src/components/Auth.tsx`.
- Replace promotional sign in layout and copy with a compact account form.
- Simplify `frontend/src/App.tsx` navigation and `frontend/src/components/UI.tsx` shared presentation.
- Establish neutral and blue design tokens, controls, navigation and responsive shell styles.
- Build and commit this independently testable stage.

## 2. Dashboard and routes

- Remove the decorative dashboard promotion; retain metrics, chart, recent orders, and route rankings.
- Use concise labels in `Dashboard.tsx` and `Routes.tsx`; preserve route photos for identification.
- Style metric summaries, tables, route tiles, search and filters for desktop and mobile.
- Build and commit this stage.

## 3. Management views and polish

- Align orders, users, account, booking and edit dialogs with the shared visual system.
- Check all three roles and responsive widths in a real browser. Correct visible defects.
- Run frontend build and format checks, review the Git diff, then commit the final UI changes.

## 4. GitHub review

- Push the feature branch and open a PR against `main` with the change summary and verification evidence.
