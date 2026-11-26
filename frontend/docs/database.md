# FarmCrop database boundary

The frontend no longer contains a database client, SQL schema, migration, or cloud database integration.

The planned backend is Spring Boot with MySQL. It will own:

- Authentication and sessions.
- Profiles and Seller applications.
- Seller approval and suspension status.
- Crop listings and inventory.
- Orders and order history.
- Payments and settlements.
- Authorization for Buyer, Seller, and administrator operations.

The current frontend deliberately does not claim that these operations are connected. Configure `VITE_API_BASE_URL` only after a compatible Spring Boot API is available.
