---
id: 09dbaab0-e814-49e3-b318-ff00999916dd
title: Writing a Dockerfile
order: 1
estimatedTime: 15
---
# Writing a Dockerfile

A `Dockerfile` lists the steps used to build an image.

```dockerfile
FROM node:22-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
EXPOSE 3000
CMD ["node", "server.js"]
```

- `FROM` chooses the base image.
- `WORKDIR` sets the working directory.
- `COPY` and `RUN` add files and run commands at build time.
- `CMD` is the command that runs when a container starts.

Copy `package*.json` and install dependencies *before* copying the rest of the code so Docker can reuse cached layers.
