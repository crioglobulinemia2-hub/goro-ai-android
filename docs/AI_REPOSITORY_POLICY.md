# Repository governance for AI-assisted development

This repository uses one canonical state and evidence-based integration.

## Canonical state
- `main` is the only canonical product history.
- Work starts from the current `main` SHA. Record that base SHA in the PR.
- A PR head SHA is the only candidate being reviewed. Agents must cite that exact SHA.
- If `main` advances, the candidate must be updated before review continues.

## Branch and merge rules
- No agent or automation may write directly to `main`.
- Changes enter `main` only through a pull request.
- One objective has one integration branch. Parallel agent branches are temporary inputs, not competing releases.
- The integrator resolves all agent outputs into one candidate branch before CI and review.

## Required evidence
- CI must run on the candidate SHA.
- A build or unit-test pass is not proof of end-to-end functionality.
- Claims involving Android, OAuth, NVIDIA services, Bluetooth/ZNP, audio, display, signing, or physical hardware require the corresponding real integration evidence.
- Missing evidence means BLOCKED, not DONE.

## Multi-agent rule
- Producer and auditor must review the same candidate SHA.
- Audit reports must identify SHA, files/lines, evidence, reproduction, proposed fix, and verification.
- No result from another branch or older SHA can be used to approve the current candidate.

## Release rule
A release can be called final only when all mandatory product gates have evidence attached to the same candidate SHA and the resulting artifact is traceable to that SHA.
