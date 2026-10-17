# API Reference

## Backend API

### Auth
- `POST /api/auth/register` - Register a new user
- `POST /api/auth/login` - Login

### Devices
- `POST /api/devices` - Register a new device
- `GET /api/devices` - List devices
- `DELETE /api/devices/{id}` - Deactivate device

### Events
- `POST /api/events` - Ingest batch of activity events

## ML Service API
- `POST /classify` - Classify an event
- `POST /feedback` - User feedback on classification
- `GET /health` - Health check
