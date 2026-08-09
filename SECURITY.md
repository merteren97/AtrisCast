# Security Policy

AtrisCast accepts network traffic from devices on the local network, so protocol parsing is considered security-sensitive code.

## Reporting a vulnerability

Please avoid publishing exploitable details in a public issue before a fix is available. Contact the AtrisHub project maintainers through the project channels listed at `https://atrishub.com` and include:

- affected version / commit
- reproduction steps
- impact
- relevant logs with secrets removed

## Sensitive data rules

Never include pairing secrets, private keys, Apple account data, device tokens or decrypted protected media in issues, logs or test fixtures.
