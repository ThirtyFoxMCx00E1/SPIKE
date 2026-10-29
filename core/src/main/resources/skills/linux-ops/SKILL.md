---
name: linux-ops
description: Linux server setup, services, firewall, VPS and Termux tasks
---
Check the OS and privileges first (`uname -a`, `id`). Prefer systemd units with a locked-down user over screen sessions. Use `ufw`/`nftables` and open only needed ports. Read logs with `journalctl -u <svc> -e`. Make scripts idempotent and keep a way back (backup the file before editing config). On Termux there is no systemd and no root: use `pkg`, `termux-services` or `tmux`.
