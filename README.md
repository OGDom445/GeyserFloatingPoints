> **Unofficial compatibility fork maintained by OGDom445.** Original project and author: [oryxel1/GeyserFloatingPoints](https://github.com/oryxel1/GeyserFloatingPoints), by oryxel. This fork is not an official release or endorsed by the original creator.
>
> Modified October 15, 2026 with assistance from OpenAI Codex. Distributed under the [GNU GPL v3](LICENSE). See [PATCH-NOTES.md](PATCH-NOTES.md) for changes, installation, tested builds, and limitations. The release is a prerelease pending live gameplay verification.

# GeyserFloatingPoints
Workaround to fix floating-point precision on Bedrock when at high coordinate.

## How this works
- Simply works by spoofing position on Bedrock player side, making it seems like they're still close to 0 0 0 coordinate.

## Commands
- `/geyserfloatingpoints position` - Toggle on/off actionbar that show your real position. Permission: `geyserfloatingpoints.position`

## Drawbacks
- You won't be able to see your real position since your position is always spoofed.
- Bugs are bound to happened, feel free to report ems :)
