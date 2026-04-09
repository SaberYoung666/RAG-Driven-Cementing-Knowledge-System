FROM node:22-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm config set registry https://registry.npmjs.org/ \
    && npm config set fetch-retry-maxtimeout 120000 \
    && npm config set fetch-retries 5 \
    && npm config set fetch-timeout 120000 \
    && npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine

COPY nginx/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
