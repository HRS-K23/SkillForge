---
title: Clone, commit and push
order: 2
estimatedTime: 15
---
# Clone, commit and push

Work happens on your computer and is synced to GitHub.

```bash
# copy the repository to your machine
git clone https://github.com/<your-username>/practice-project.git
cd practice-project

# make a change, then record it
echo "Hello SkillForge" > hello.txt
git add hello.txt
git commit -m "Add hello file"

# upload the commit to GitHub
git push
```

## What each command does

- `git clone` downloads a repository.
- `git add` chooses which changes go into the next commit.
- `git commit` saves those changes with a message.
- `git push` sends your commits to GitHub.

Write commit messages that describe *why* the change was made.
