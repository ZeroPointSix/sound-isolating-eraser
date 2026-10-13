# Bone Spike Gallery Framing Regression

This is a test-fixture correction, not a production behavior change or final functional acceptance.

- Failing source: `44187311b565863c314b0a47a6c92fd22ab2f1e7`.
- [Failed real-client job](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38242416232/job/114788021556).
- [Original 76-file client artifact](https://github.com/ZeroPointSix/sound-isolating-eraser/actions/runs/38242416232/artifacts/11744141160).
- Artifact ZIP SHA-256: `af6d65bf744241cacdda0ef99155482d5ac7328503ed95b63fd75bf0946cfc47`.
- Raw job log SHA-256: `8a8f80005d4e94ec1d3595661207834ce71f7409b7bf5089b9be6d146d1d415a`.

## Observed Failure

The real flying-spike model was present, but its foreground difference reached the bottom of the central inspection rectangle: `changed=9251, bounds=416,184,572,485`, camera `(1.5,65.0,1.5)`. Visual inspection shows the spike's floor shadow close to that lower boundary. The first 11 monster profiles and their AI/audio probes had passed; the bone gallery failed before wall-impact and subsequent multi-client stages. This run is not a client-suite pass.

- [Unchanged failing screenshot](bone_spike.png)
- [Unchanged hidden-entity control screenshot](bone_spike-control.png)
- [Failure marker](client.failed)
- [Complete failing job log](client-job.log)

## Correction And Validation Boundary

Only the flying-spike gallery camera is moved back, from `(1.5,65,1.5)` to `(2.5,65,2.5)`, with pitch adjusted from `23.7` to `12.4` degrees to keep the spike centered. This includes the model, particles and floor shadow in the same view. The central-pixel minimum, stability maximum and strict edge-framing assertions remain unchanged. Production renderers, physics, wall collision, 200-tick expiration, impact audio and all other assertions are unchanged.

The fixing commit must be checked by its own CI run. Results and clickable current-HEAD job/artifact links are recorded in PR #21; older green runs are historical evidence only. Previously archived originals under `814a6a2`, `6e5eea4`, `ddfa448`, `c84d2b1`, and `takeover-20261012` are retained.
