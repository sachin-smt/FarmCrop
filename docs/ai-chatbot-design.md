# AI chatbot design

## Access

The chatbot is an independent backend feature intended for authenticated
buyers and sellers. Authorization must ensure users can only access their own
conversations.

## Conversations and message history

Conversation and message entities and repositories are represented by
placeholders. MySQL persistence, ownership rules, retention, and history
endpoints have not been implemented.

## Provider integration

The AI provider has not been selected. `AiProviderService` and `AiConfig` are
placeholders only; no provider SDK, external connection, or sample response is
included.

## Authentication and authorization

Future chatbot endpoints must use the application's authentication and
role-based authorization. Conversation ownership must be checked for reads and
writes.

## Language support

Future design may include Hindi and English. Language detection, user
preferences, and translation behavior remain undecided.

## API key handling

Future AI API keys must be supplied through environment variables or a secret
manager. They must never be committed to source control, returned in API
responses, or written to logs.
