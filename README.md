# jenkins-atom-build-name-setter

The [build-name-setter](https://plugins.jenkins.io/build-name-setter/) plugin (≈28k installations) as an **atom**: one
Java file, no plugin, no dependency.

Renames the build it runs in and sets its description:

* `displayName` / `description` — templates, with `${ENV}` expanded from the build environment
  (`BUILD_NUMBER`, `GIT_COMMIT`, anything the job exports).
* `fromLog` / `matchTemplate` — take the name from a regex over the console log the runner hands over
  (`x-jenkins.context: ["log"]`, last 2 MiB), with `%MATCH%` standing for the first capture group.

## How it reaches Jenkins

An atom is an external process, so it cannot touch the Jenkins API. It writes `mutations.json` into its data directory
and the runner applies it:

```json
{"displayName": "release-7-abc1234", "description": "built by atom"}
```

Only `displayName` and `description` are accepted, and every applied change is printed in the console — an atom can
rename its own build, nothing else.

## Build and smoke test

```bash
./build.sh		# → dist/build-name-setter/{build-name-setter.jar,task.json}
./tools/smoke.sh	# runs the atom the way the runner does: input.json + build.log in, mutations.json out
```

The smoke test covers the template path, the log-regex path, and the case where the task forgot to declare the log —
that last one must fail with a readable message instead of silently keeping the template.

## Publish

```bash
tools/publish-atom.sh --center <center-dir> --atom-dir dist/build-name-setter --version 1.0.0 \
    --key key.pem --key-id <keyId>     # tools/ 来自 jenkins-atom-plugin 仓库
```

Requires a runner with `x-jenkins.context` and `mutations.json` (runner ≥ 0.3).
