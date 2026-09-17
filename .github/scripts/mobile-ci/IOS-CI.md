# iOS build diagnostics

The consumer and enterprise jobs retain `wallet-core-build-diagnostics` and
`enterprise-ios-build-diagnostics` artifacts for seven days. Uploads run outside
the timed build action so its timeout does not skip collection.

Each wrapped phase produces a command log, JSON start/end/exit metadata and JSONL
resource samples every 30 seconds. Samples include process CPU/RSS, VM page
counters, swap and filesystem usage, but not process arguments or environment
values. The console emits a heartbeat; detailed command output is in the artifact.
Resource probes are best-effort and bounded. Command failures and cancellation
remain failures even if the child exits successfully while handling termination.

Enterprise diagnostics additionally separate framework compilation, fixture
readiness, Xcode build/tests and fixture cleanup. Gradle HTML profiles are retained
alongside these logs. An incomplete phase record identifies interruption; it is
not a successful test result. Compare exact source/toolchain revisions and cache
restore keys before attributing a timing difference to code.

The enterprise execution budget is provisionally 75 minutes, with a 90-minute job
limit to allow setup and report collection. This supplies headroom while native
cache and resource behavior are measured; it is not a build speed improvement.
The selected tests and release framework variants remain unchanged.

Run the diagnostic-runner regression checks with:

```sh
python3 -m unittest discover -s .github/scripts/mobile-ci -p test_ios_phase.py
```
