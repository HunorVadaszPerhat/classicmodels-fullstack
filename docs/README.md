# Classic Models — Learning Docs

This folder is the running record of features being added to the
Classic Models app, with study material aimed at someone learning the
technologies hands-on.

Every feature has its own markdown file. Each file follows the same
shape:

1. **What we built** — one paragraph summary.
2. **Why this is worth learning** — what concept it's teaching.
3. **Background** — short explanations of any new terms, with links
   to deeper reading.
4. **The code, walked through** — only the interesting pieces; the
   full diff lives in git.
5. **How to test** — concrete steps to verify it works.
6. **What you just learned** — a recap, to anchor the new ideas.
7. **Study materials** — links and book/video recommendations for
   going deeper on any of the topics.

## Feature index

The 18-feature plan, grouped by theme.

### A. Map refinements

- [01 — Rich popup on map marker](employees/01-rich-marker-popup.md) ✓
- [02 — Office detail page with team + map](employees/02-office-detail-with-team.md) ✓
- [03 — Global office overview map](employees/03-global-office-map.md) ✓
- [04 — Geocode an office on demand](employees/04-geocode-on-demand.md) ✓

### B. Production-grade backend patterns

- [05 — Audit columns + auto-population](employees/05-audit-columns.md) ✓
- [06 — Server-side pagination, sorting, filtering](employees/06-server-side-pagination.md) ✓
- [07 — Optimistic locking](employees/07-optimistic-locking.md) ✓

### C. Cross-stack features

- [08 — Reassign-and-delete workflow](employees/08-reassign-and-delete.md) ✓
- [09 — Photo uploads](employees/09-photo-uploads.md) ✓
- [10 — Live updates via WebSocket](employees/10-live-updates-websocket.md) ✓

### D. Data visualization

- [11 — Org chart with D3](employees/11-org-chart-d3.md) ✓
- [12 — Sales dashboard with Chart.js](employees/12-sales-dashboard-chartjs.md) ✓
- [13 — Customer lifetime value](employees/13-customer-lifetime-value.md) ✓

### E. Bulk operations

- [14 — Multi-select + bulk actions](employees/14-multi-select-bulk-actions.md) ✓

### F. Auth extension

- [15 — OAuth2 with Google sign-in](employees/15-oauth2-google-signin.md) ✓

### G. Operational tooling

- [16 — Spring Boot Actuator + Prometheus](employees/16-actuator-prometheus.md) ✓
- [17 — Docker Compose for the full stack](employees/17-docker-compose-fullstack.md) ✓
- 18 — GitHub Actions CI (pending)

## How to read these docs

Pick one and read top-to-bottom. Skip the **Background** section
entirely if the concepts there are already familiar; the linked
external resources are the canonical place to learn each topic, this
file just collects the relevant ones in one place.

The **Study materials** at the bottom are deliberately a mix of
official docs, blog posts, and book/video pointers. Some people prefer
reference docs; others prefer narrative explanations. There's
something in each list for both styles.
