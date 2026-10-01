---
id: 79105e1e-b13e-4e35-9c23-8fe759314367
title: Building and tagging images
order: 2
estimatedTime: 10
---
# Building and tagging images

```bash
docker build -t my-app:1.0 .
docker run -p 3000:3000 my-app:1.0
```

- `-t` names and tags the image (`name:tag`).
- The trailing `.` is the build context, the folder sent to Docker.

Add a `.dockerignore` file listing things such as `node_modules` and `.git` to keep images small and builds fast.
