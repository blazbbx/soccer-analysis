# syntax=docker/dockerfile:1.7

# ----------------------------------------
# Build stage — install deps, type-check,
# bundle the SPA with Vite.
# ----------------------------------------
FROM node:22-alpine AS build

WORKDIR /app

# Copy lockfile first so the dep install layer is cached unless package*.json
# changes. The BuildKit cache mount keeps npm's tarball cache across rebuilds,
# so adding or bumping a single dep doesn't redownload everything from the
# registry — same idea as the uv cache mount we use in the python services.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

# Copy the rest of the source and build. `npm run build` = `tsc -b && vite build`,
# so TypeScript errors fail the build (intentional).
COPY . .
RUN npm run build

# ----------------------------------------
# Runtime stage — static nginx serving the
# Vite-built bundle.
# ----------------------------------------
FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
