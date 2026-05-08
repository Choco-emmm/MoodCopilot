# MoodCopilot

MoodCopilot is an AI emotion diary and stranger-support community.

The first version focuses on this loop:

1. Write a private or public diary entry.
2. Let AI generate emotion labels, topic labels, a summary, and a gentle response.
3. Recommend similar public diaries after publishing.
4. Allow comments and resonance reactions for supportive interaction.

## Tech Stack

- Backend: Spring Boot 3, Java 21, MySQL, Redis
- Frontend: Vue 3, Vite, TypeScript, mobile-first H5
- AI provider: DeepSeek-compatible chat API, configured locally

## Project Layout

```text
backend/   Spring Boot API service
frontend/  Vue mobile-first web app
docs/      Product and engineering notes
```

## Local Configuration

Copy the backend example config before running locally:

```text
backend/src/main/resources/application-local.example.yml
```

to:

```text
backend/src/main/resources/application-local.yml
```

Then fill in your own MySQL, Redis, and DeepSeek values. Do not commit real secrets.

