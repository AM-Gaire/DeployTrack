# React compiles to static files. There is no Node process in production --
# the build stage produces HTML, CSS and JavaScript, and Nginx serves them.
# Shipping the Node toolchain to production would be several hundred MB of
# attack surface for something that never runs.

FROM node:22-alpine AS build
WORKDIR /build

# package files first so the dependency layer is cached independently of source
# changes -- editing a component should not trigger a reinstall.
COPY package.json package-lock.json ./
# npm ci installs exactly what the lockfile pins, and fails if the two have
# drifted. npm install would silently resolve newer versions, so the image
# could differ from what was tested.
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime

COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# Nginx daemonises by default, which would exit the foreground process and stop
# the container immediately. Docker needs the process to stay in the foreground.
CMD ["nginx", "-g", "daemon off;"]
