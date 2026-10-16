# Workspace UI redesign

## Context

The current frontend is React 19 and TypeScript. The earlier classroom version was Java Servlet/JSP, not Vue. The backend API and three roles remain in place.

## Direction

Use Google Workspace as a visual reference: a light application shell, a quiet neutral canvas, blue as the primary action color, clear toolbar hierarchy, compact data presentation, and consistent focus and hover states. Keep the travel photography where it identifies an actual route. Do not copy Google branding.

## Scope

- Remove the demo account picker, displayed demo password, and promotional image/copy from sign in. Keep ordinary sign in and traveler registration. Demo credentials remain in the README for local evaluation.
- Replace the dark sidebar and decorative dashboard with a light navigation rail, useful metrics, a trend chart, recent orders, and route rankings.
- Make route browsing, order tabs and tables, user management, account settings, dialogs, forms, status indicators, empty states, and pagination visually consistent.
- Keep all current API contracts, role permissions, CSRF handling, and business rules.
- Support desktop and mobile widths, keyboard focus, readable contrast, and no overlapping controls.

## Verification

Build the frontend, check formatting, inspect the sign in and all three role views at desktop and mobile widths, and exercise navigation plus representative read-only controls. Use a feature branch with meaningful commits and open a PR against `main`.
