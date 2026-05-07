# =============================================================================
# Frontend Dockerfile — multi-stage Node build → nginx-alpine serve.
# =============================================================================
# Stage 1 ("build"):
#   - Pulls Node.js to install npm dependencies and run `ng build`.
#   - Caches node_modules across builds: package*.json copied first, install
#     runs once unless the lockfile changes; src/ copied second, build
#     re-runs on every source change but skips the install step.
#   - `ng build --configuration=production` outputs static assets into
#     /app/dist/classicmodels-ui/browser/ (Angular's default for SSR-aware
#     builds; without SSR the browser/ subdir still appears in v17+).
#
# Stage 2 ("runtime"):
#   - Pulls nginx-alpine (~25MB).
#   - Copies the built static assets into nginx's default web root.
#   - Replaces nginx's default config with our own that:
#       1. Serves the SPA at "/" and falls back to /index.html for
#          HTML5 push-state routes (so /employees/123 doesn't 404 when
#          someone reloads or hits the URL directly).
#       2. Reverse-proxies /api/* to the backend service inside the
#          compose network.
#   - Final image ~30MB.
# =============================================================================

# ----- BUILD STAGE -----------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /app

# --- Layer 1: dependencies. npm ci is faster + reproducible vs npm install:
#   - reads package-lock.json strictly
#   - errors if lockfile is out of sync
#   - wipes node_modules first
# This step's layer cache is invalidated only by changes to package*.json.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# --- Layer 2: source + build. Re-runs on every source change.
COPY . .

# `ng build` defaults to the production configuration in v17+. We pass it
# explicitly for clarity. Output is in /app/dist/classicmodels-ui/browser/.
RUN npx ng build --configuration=production

# ----- RUNTIME STAGE ---------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# Drop nginx's default index.html so we don't accidentally fall back to
# the welcome page if our copy step misses something.
RUN rm -rf /usr/share/nginx/html/*

# Copy our SPA assets. Angular 17+ outputs to dist/<project>/browser/
# (the SSR/non-SSR split). For our project name "classicmodels-ui",
# the path matches what `ng build` writes.
COPY --from=build /app/dist/classicmodels-ui/browser/ /usr/share/nginx/html/

# Replace the default site config with ours. The base nginx.conf and
# event/http blocks come from the official image; this file only
# customizes the server{} block.
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# Run nginx in the foreground so the container's main process stays alive.
# The default ENTRYPOINT in the nginx image already does this, but
# making it explicit beats inheriting silent assumptions.
CMD ["nginx", "-g", "daemon off;"]
