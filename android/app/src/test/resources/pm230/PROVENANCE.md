# PM230 golden-test fixtures

`job_bitmap.bin` and `job_expected.bin` are extracted verbatim from an Android HCI
snoop capture of the official Nelko app, and `LICENSE` is the license of the project
they came from:

- Source: https://github.com/an21p/nelko-pm230-driver (commit 935c7908), MIT License
- Files: tests/fixtures/job_bitmap.bin, tests/fixtures/job_expected.bin, LICENSE

They exist so TsplGoldenTest can prove our TSPL job builder reproduces the captured
vendor job byte for byte — the acceptance gate for PM230 printing.
