# Project context and efficient navigation

- At the start of a new coding task, read `PROJECT_CONTEXT.md` once before broad repository exploration. It is the project map, not a replacement for the source files being changed.
- Check `git status --short` and compare the current revision with the context baseline. Preserve existing user changes. When context is stale, inspect the relevant diff and update only affected sections.
- Use the task-to-code table in the context to open the relevant implementation, callers and tests. Search within those directories first; expand only when dependencies or uncertainty require it. Avoid repeatedly listing or reading the entire repository.
- Source code, migrations and current configuration take precedence over descriptive documentation. Verify affected behavior before editing; do not assume a historical PASS result proves the current checkout works.
- Skip dependency/build trees, bundled Maven, large CSVs, extracted Office XML and logs during normal orientation. Never copy `.env` values or private credentials into context or responses.
- After changing architecture, module boundaries, API contracts, schema, commands or known limitations, update the corresponding part of `PROJECT_CONTEXT.md` in the same task. Keep it concise; do not append chat transcripts or routine activity logs. For a UI-only change, update context only if navigation or a documented contract changed.
- Run checks appropriate to the change using the context's verification commands. Documentation-only changes need link/path and diff checks, not the full application test suite.
