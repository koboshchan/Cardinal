# Cardinal

Cardinal System (カーディナル・システム, Kādinaru Shisutemu) from Sword Art Online, that generates quests with llms for Quests plugin in minecraft

It supports automated story-based quest generation, category creation, periodic generation, and admin commands for init, generate, reload, and status.

*Note: This plugin is for [Quests](https://modrinth.com/plugin/quests) plugin. Not [Quests](https://modrinth.com/plugin/quests.classic)*

## Commands

- `/cardinal init <story_prompt>`: Initialize story context and generate the first quest batch.
- `/cardinal generate <amount>`: Generate additional quests from the current story context.
- `/cardinal reload`: Reload Cardinal configuration.
- `/cardinal status`: Show total LLM input and output token usage.

## Configuration

Edit `plugins/Cardinal/config.yml` after first startup.

```yml
enabled: true

llm:
	base_url: "https://api.openai.com/v1"
	api_key: "your-api-key-here"
	model: "gpt-3.5-turbo"
	temperature: 0.7
	max_tokens: 2048
	timeout_seconds: 30

generation:
	days_per_generation: 1
	quests_per_batch: 3
	max_total_quests: 50
	output_folder: "quests/generated"
	verbose_logging: true

story:
	context: ""
	category_prefix: "story"

tokens:
	calculate_spendings: false
	price_per_million_input: 0.0
	price_per_million_output: 0.0
```

Notes:
- `llm.api_key` is required.
- `generation.max_total_quests` can be set to `-1` for no limit.
- Token usage is persisted by Cardinal under `tokens.total_input` and `tokens.total_output`.
- Enable `tokens.calculate_spendings` to show estimated costs in `/cardinal status`.

## Build

Requirements:
- Java 21

Build commands:

```bash
./gradlew clean build -x test
```

Build output JARs are written to:
- `build/libs/`

## Install

1. Build the plugin.
2. Copy the generated JAR from `build/libs/` into your server `plugins/` folder.
3. Ensure Quests is installed (Cardinal depends on it).
4. Start server, configure `plugins/Cardinal/config.yml`, then run `/cardinal init <story_prompt>`.
