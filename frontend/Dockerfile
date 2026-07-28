# --- Build stage: compile the Vite app to static assets ---
FROM node:22-alpine AS build
WORKDIR /app

# Install deps against the lockfile first so this layer caches across code changes.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# VITE_API_URL is baked in at build time; override with --build-arg for prod.
ARG VITE_API_URL=http://localhost:8080/api/v1
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# --- Runtime stage: serve the static bundle with nginx ---
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD wget -q --spider http://localhost/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
