#
# Simple container that runs the DC API test app.
#
FROM node:20-alpine
WORKDIR /app

# Workspace manifests
COPY package.json package-lock.json ./

# Required workspaces
COPY apps/dc-api-test apps/dc-api-test

RUN npm ci

ENV HOST=0.0.0.0
ENV PORT=80
EXPOSE 80

# Run Vite dev server
CMD ["npm", "run", "dev", "--workspace", "apps/dc-api-test", "--", "--host", "0.0.0.0", "--port", "80"]
