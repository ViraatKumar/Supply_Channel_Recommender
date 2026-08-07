FROM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf.template
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
CMD ["/bin/sh", "-c", "envsubst '${PORT} ${BACKEND_UPSTREAM}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]
