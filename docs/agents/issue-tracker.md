# Issue tracker: GitHub

Issues and PRDs for this repository live as GitHub Issues. Use the `gh` CLI for operations and infer the repository from `git remote -v`.

## Conventions

- Create an issue with `gh issue create --title "..." --body "..."`.
- Read an issue with `gh issue view <number> --comments` and include its labels.
- List issues with `gh issue list`, using state and label filters appropriate to the task.
- Comment with `gh issue comment <number> --body "..."`.
- Apply or remove labels with `gh issue edit <number> --add-label "..."` or `--remove-label "..."`.
- Close an issue with `gh issue close <number> --comment "..."`.

When a skill says to publish to the issue tracker, create a GitHub Issue. When it says to fetch the relevant ticket, use `gh issue view <number> --comments`.
