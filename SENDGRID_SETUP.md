# SendGrid Email Configuration Guide

## Issue: "The from address does not match a verified Sender Identity"

This error occurs when the sender email address used in `application.properties` is NOT verified in your SendGrid account.

## Current Configuration

**Default Sender Email:** `noreply@neurascan.app` (configurable via `SENDGRID_FROM_EMAIL` environment variable)

## Step-by-Step Fix

### 1. **Check Current Sender Email**

Look at your server logs. They will show:
```
→ Sender email used: [actual-email-from-config]
```

### 2. **Verify Sender in SendGrid Dashboard**

1. Go to: https://app.sendgrid.com/settings/sender_auth
2. Click **"Verify a Single Sender"**
3. Enter the sender email address (e.g., `noreply@neurascan.app` or your personal email)
4. Check your email inbox and **click the verification link**
5. Mark as verified ✅

### 3. **Update Application Configuration**

#### Option A: Using Environment Variable (Recommended for Production)

Set the environment variable with your verified email:
```bash
export SENDGRID_FROM_EMAIL=your-verified-email@domain.com
export SENDGRID_API_KEY=SG.your_actual_api_key_here
```

#### Option B: Direct Configuration Change (Development Only)

Edit `src/main/resources/application.properties`:
```properties
sendgrid.from.email=your-verified-email@domain.com
```

### 4. **Get SendGrid API Key**

1. Go to: https://app.sendgrid.com/settings/api_keys
2. Click **"Create API Key"**
3. Set permissions: 
   - ✅ Mail Send
   - ✅ Read
4. Copy the key (format: `SG.xxxxxxxxxxxxx`)
5. Set as environment variable:
   ```bash
   export SENDGRID_API_KEY=SG.your_actual_api_key_here
   ```

## Quick Reference

| Item | Value | Where to Set |
|------|-------|-------------|
| Sender Email | Your verified email | `SENDGRID_FROM_EMAIL` env var OR `application.properties` |
| API Key | Your SendGrid API key | `SENDGRID_API_KEY` env var |
| Verification | Must match a verified "Single Sender" | SendGrid Dashboard → Sender Auth |

## Testing Email Functionality

### Via API
```bash
curl -X POST http://localhost:8080/api/auth/send-otp \
  -H "Content-Type: application/json" \
  -d '{"email":"test@gmail.com"}'
```

### Via Frontend
1. Navigate to the Password Reset or Registration page
2. Enter email address
3. Check server logs for confirmation or errors

## Common Issues & Solutions

### Issue: 403 Forbidden - "verified Sender Identity"
**Solution:** Verify your sender email in SendGrid dashboard (see Step 2 above)

### Issue: 401 Unauthorized - "Invalid API key"
**Solution:** Check that `SENDGRID_API_KEY` environment variable is set correctly

### Issue: Email not received
**Possible causes:**
- Sender email not verified
- Email in spam folder
- Recipient mailbox full
- API key has wrong permissions

**Solution:** Check server logs for detailed error messages with `From:` email shown

## Environment Variables Summary

```bash
# Required for production
SENDGRID_API_KEY=SG.your_actual_key_here
SENDGRID_FROM_EMAIL=your-verified-email@domain.com

# Optional (defaults provided)
APP_FRONTEND_URL=https://your-frontend-url.com
AI_SERVICE_URL=https://your-ai-service.com
JWT_SECRET=your-jwt-secret-key
```

## Verifying Configuration at Startup

When the application starts, check logs for:
```
📧 Sending email to: user@example.com | From: your-verified-email@domain.com
✅ Email sent successfully
```

If you see errors, fix them following the steps above.

---

**Last Updated:** March 18, 2026
**Application:** NeuraScan Backend
**Service:** SendGrid Email
