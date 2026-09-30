---
title: Running your first container
order: 2
estimatedTime: 12
---
# Running your first container

After installing Docker Desktop (or Rancher Desktop), open a terminal:

```bash
docker --version
docker run hello-world
```

The first command checks the installation. The second downloads the `hello-world` image and runs it.

## Useful commands

```bash
docker ps          # running containers
docker ps -a       # all containers
docker images      # local images
docker stop <id>   # stop a container
docker rm <id>     # delete a container
```
