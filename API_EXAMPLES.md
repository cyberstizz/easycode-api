# API examples

Every call, in the order you'd actually make them. Paste into the VS Code **REST Client**
extension (`.http` syntax) or read it as a reference and rebuild the calls in Postman.

```http
### EasyCode API — paste into VS Code REST Client or Postman
@base = http://localhost:8080
@token = paste-the-accessToken-here

### Health
GET {{base}}/v1/public/health

### Marketing contact form (public, validates for real)
POST {{base}}/v1/public/contact
Content-Type: application/json

{ "name": "Sam Rivera", "email": "sam@example.com", "phone": "9175550142",
  "business": "Rivera Barbershop", "message": "Need a site and booking." }

### Log in
POST {{base}}/v1/auth/login
Content-Type: application/json

{ "email": "you@easycode.dev", "password": "your-bootstrap-password" }

### Create a client
POST {{base}}/v1/admin/organizations
Authorization: Bearer {{token}}
Content-Type: application/json

{ "name": "Rivera Barbershop", "industry": "Personal care", "dealTier": "PREFERRED" }

### Add the primary contact
POST {{base}}/v1/admin/organizations/{{orgId}}/contacts
Authorization: Bearer {{token}}
Content-Type: application/json

{ "name": "Sam Rivera", "email": "sam@example.com", "isPrimary": true }

### Send the portal invite (link prints to the log when RESEND_ENABLED=false)
POST {{base}}/v1/admin/organizations/contacts/{{contactId}}/invite
Authorization: Bearer {{token}}

### Start the project — creates all six tracker stages
POST {{base}}/v1/admin/projects
Authorization: Bearer {{token}}
Content-Type: application/json

{ "orgId": "{{orgId}}", "name": "Rivera Barbershop website",
  "type": "Website", "contractCents": 120000, "depositCents": 20000 }

### Advance the tracker
POST {{base}}/v1/admin/projects/{{projectId}}/advance
Authorization: Bearer {{token}}

### Client files a request
POST {{base}}/v1/requests
Authorization: Bearer {{token}}
Content-Type: application/json

{ "projectId": "{{projectId}}", "type": "UPDATE",
  "title": "Swap the hero photo", "body": "New shot attached.", "priority": "NORMAL" }

### Triage it as billable
PATCH {{base}}/v1/requests/{{requestId}}
Authorization: Bearer {{token}}
Content-Type: application/json

{ "status": "IN_PROGRESS", "billing": "BILLABLE" }

### Propose the change order
POST {{base}}/v1/requests/{{requestId}}/change-orders
Authorization: Bearer {{token}}
Content-Type: application/json

{ "amountCents": 15000, "description": "New hero photography + placement" }

### Client approves — raises and sends the invoice in one move
POST {{base}}/v1/change-orders/{{changeOrderId}}/approve
Authorization: Bearer {{token}}

### Pay it in-app (returns the Payment Element client_secret)
POST {{base}}/v1/invoices/{{invoiceId}}/payment-intent
Authorization: Bearer {{token}}

### Upload, step 1 of 3
POST {{base}}/v1/assets/presign
Authorization: Bearer {{token}}
Content-Type: application/json

{ "projectId": "{{projectId}}", "filename": "hero.jpg",
  "mime": "image/jpeg", "bytes": 842113 }
```
