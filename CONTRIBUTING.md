# Contributing to AtrisCast

Thanks for helping improve AtrisCast.

## Development principles

- Keep casting local-first and account-free.
- Prefer Android platform APIs over large media/native frameworks when they satisfy the requirement.
- Keep discovery, protocol, crypto and media layers separated.
- Add tests for parsers, state transitions and cryptographic protocol code.
- Treat input from LAN clients as hostile.
- Keep commits focused and explain protocol behavior in code comments where interoperability is non-obvious.

## Licensing

AtrisCast core is Apache-2.0. Before copying or adapting third-party source code, verify the upstream license and preserve required notices. GPL code must not be copied into the Apache-licensed core without an explicit project licensing decision.

## Pull requests

A good PR should state:

- what changed
- why it is needed
- how it was tested
- which sender/TV versions were tested for protocol changes
- whether third-party code or protocol constants were introduced
