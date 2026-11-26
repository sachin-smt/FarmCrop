# FarmCrop requirements

This document records the initial product scope. It is not an implementation
specification; APIs and business rules remain to be designed.

## Buyer

- Browse and search crop listings.
- View listing details and seller information.
- Place and track orders.
- Communicate with sellers.
- Use the AI assistant when that feature is designed and enabled.

## Seller

- Register and submit information and documents for verification.
- Create and manage crop listings.
- Review and manage orders for listed crops.
- Use the AI assistant when that feature is designed and enabled.

## Admin

- Manage marketplace users.
- Review seller verification applications and approve or reject them.
- Monitor marketplace activity.

## AI chatbot

- Provide an independently organized assistance module for buyers and sellers.
- Support conversations and message history in a future implementation.
- Keep provider selection and external AI integration deferred.

## Security

- Protect authenticated buyer, seller, and administrator capabilities with
  Spring Security and a future JWT-based authentication flow.
- Apply role-appropriate authorization to user and marketplace operations.
- Keep database credentials, JWT secrets, and future AI API keys outside source
  control and supply sensitive values through environment or secret management.
- Do not expose verification documents or conversation history to unauthorized
  users.
