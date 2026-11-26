# Planned database design

The following entities are planned for future design. No database tables,
relationships, constraints, or seed data have been created.

- **User**: shared account identity and account status.
- **Seller**: seller-specific profile and verification state.
- **Crop**: a seller-owned crop listing.
- **Order**: a buyer's order for listed crops.
- **Payment**: payment information associated with an order.
- **Verification**: seller application, submitted documents, and review state.
- **Conversation**: a buyer/seller chatbot conversation.
- **ChatMessage**: a message belonging to a conversation.
- **Notification**: a notification addressed to a user.

Relationships, field definitions, retention rules, and indexes require
confirmation before schema migrations are implemented.
