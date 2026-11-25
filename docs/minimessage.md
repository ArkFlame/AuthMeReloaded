# MiniMessage support

AuthMe can optionally interpret [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
tags in its message files (for example `<red>Login successful</red>`).

Enable it in `config.yml`:

```yaml
settings:
  miniMessage:
    enabled: false
```

The setting is `false` by default, so existing messages that use the legacy `&`
color codes (for example `&cUsage: /login`) behave exactly as before.

When enabled:

* Only messages that actually contain a MiniMessage tag are converted; all other
  messages keep the legacy `&` handling.
* Converted messages are emitted as legacy color codes, so every send path keeps
  working on legacy servers (including 1.8).
* Placeholders such as `<password>` and `<code>` are not treated as tags.

The required Adventure libraries (`adventure-text-minimessage` and
`adventure-text-serializer-legacy`) are declared in `plugin.yml` and downloaded
by the server at startup. If they are unavailable (for example on a legacy
server that does not load `plugin.yml` libraries), MiniMessage support is
skipped automatically and legacy color codes are used instead.
