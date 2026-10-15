# 🔑 How to Get Play Store Service Account JSON

This guide walks you through getting the `PLAY_STORE_SERVICE_ACCOUNT_JSON` secret needed for automated Play Store deployments.

---

## 📋 Prerequisites

- ✅ You must have an app published on Google Play Console (you do - WallApp!)
- ✅ You must be the owner or have admin access to the Google Play Console
- ✅ Time needed: ~10-15 minutes

---

## 🔍 Quick Navigation (Can't Find Something?)

The Play Console UI changes frequently. Here's how to find **API access**:

### Method 1: Direct URL (Fastest!)
Simply go to: **https://play.google.com/console/developers/api-access**

### Method 2: Search Bar
1. Click the **search icon** (🔍) at the top of Play Console
2. Type **"API access"**
3. Click the result

### Method 3: Settings Menu
1. Click your **profile icon** (top right)
2. Select **Account settings** or **All settings**
3. Find **API access** in the list

### Method 4: Left Sidebar (if visible)
- Look for **Settings** → **API access**
- OR **Developer account** → **API access**

**Still can't find it?** Use the direct URL in Method 1! 🎯

---

## 🎯 Step-by-Step Guide

### Step 1: Open Google Play Console

1. Go to [Google Play Console](https://play.google.com/console)
2. Sign in with your developer account
3. Select your **WallApp** (com.sky.wallapp)

---

### Step 2: Access API Settings

**New UI (2024+):**

1. Look for the **user menu** (your profile icon) in the top right corner
2. Click on **Account settings** or **All settings**
3. In the left sidebar, look for **API access** under "Developer account" or "Settings"

   OR

1. Use the search bar at the top of the Play Console
2. Type **"API access"** 
3. Click on the result

**Old UI:**

   ```
   Google Play Console
   ├── Dashboard
   ├── All applications
   ├── ...
   └── Setup
       ├── App access
       ├── App content
       └── API access  ← Click here!
   ```

**💡 Tip**: If you can't find it, search for "API access" in the Play Console search bar (magnifying glass icon at the top)

---

### Step 3: Link to Google Cloud Project

**If this is your first time:**

You'll see a message like "This app is not linked to a Google Cloud project"

1. Click **Link a Google Cloud project**
2. You'll have two options:
   - **Create a new Google Cloud project** (Recommended)
   - Link an existing project
3. Choose **Create a new Google Cloud project**
4. Click **Agree and continue**

**If already linked:**

You'll see "This app is linked to Google Cloud project: [project-name]"
- Just proceed to Step 4

---

### Step 4: Create Service Account

1. In the **API access** page, scroll down to **Service accounts**
2. Click **Create new service account**

3. You'll see instructions with a link. Click the link that says:
   > **Google Cloud Platform**
   
   This opens the Google Cloud Console in a new tab

---

### Step 5: Create Service Account in Google Cloud Console

In the Google Cloud Console:

1. You should be on the **Service Accounts** page
   - URL will look like: `console.cloud.google.com/iam-admin/serviceaccounts`

2. Click **+ CREATE SERVICE ACCOUNT** (at the top)

3. **Service account details:**
   - **Service account name**: `github-actions-wallapp` (or any name you prefer)
   - **Service account ID**: Will auto-generate (e.g., `github-actions-wallapp@...`)
   - **Description**: "Service account for automated GitHub Actions deployments"
   - Click **CREATE AND CONTINUE**

4. **Grant this service account access** (Step 2):
   - In the "Select a role" dropdown, search for: **Service Account User**
   - Select: **Service Account User**
   - Click **CONTINUE**

5. **Grant users access** (Step 3):
   - Leave blank (optional step)
   - Click **DONE**

---

### Step 6: Create and Download the JSON Key

1. You'll see your new service account in the list
2. Click on the **email address** of the service account you just created
   - It looks like: `github-actions-wallapp@project-name.iam.gserviceaccount.com`

3. Go to the **KEYS** tab at the top

4. Click **ADD KEY** → **Create new key**

5. Choose **JSON** format (should be selected by default)

6. Click **CREATE**

7. **A JSON file will download automatically** 🎉
   - It's named something like: `project-name-abc123.json`
   - **SAVE THIS FILE SECURELY** - you'll need it in the next step
   - This file contains sensitive credentials - never commit it to git!

---

### Step 7: Grant Permissions in Play Console

Now go back to the **Google Play Console** tab:

1. You might need to refresh the page (F5 or Cmd+R)
2. In **API access** → **Service accounts** section, you should now see your service account listed
3. Look for an **Actions** menu (three dots) or **Manage permissions** button next to your service account
4. Click **Manage Play Console permissions** (or **Grant access** / **View permissions**)

**In the Permissions dialog:**

5. **App permissions** (if shown):
   - Check the box next to **WallApp** 
   - OR select **"All current and future apps"** if you prefer

6. **Account permissions** (scroll down):
   Find the **Releases** section and check:
   - ✅ **View app information** (read-only)
   - ✅ **Manage production releases**
   - ✅ **Manage testing track releases**
   
   Optional (for more automation):
   - ✅ **Manage store presence**
   - ✅ **View financial data**

7. Click **Invite user** / **Apply** / **Save** (button name varies)

8. If there's a confirmation dialog, click **Send invite** / **Confirm**

**💡 Note**: The UI might show different button labels:
- "Grant access" / "Manage access" / "Manage Play Console permissions" - all mean the same thing
- "Invite user" / "Apply" / "Save" / "Send invite" - confirm the permissions

---

### Step 8: Convert JSON to Base64 and Add to GitHub

Now you have the JSON file. Let's encode it for GitHub:

1. **Open Terminal** on your Mac

2. **Navigate to where you downloaded the JSON**:
   ```bash
   cd ~/Downloads
   ```

3. **Find the JSON file**:
   ```bash
   ls -la *.json
   ```
   You should see something like `project-name-abc123.json`

4. **Convert to Base64 and copy to clipboard**:
   ```bash
   base64 -i project-name-abc123.json | pbcopy
   ```
   (Replace `project-name-abc123.json` with your actual filename)

   The encoded content is now in your clipboard! 📋

---

### Step 9: Add Secret to GitHub

1. Go to your GitHub repository:
   ```
   https://github.com/shubham-yadav-git/WallApp
   ```

2. Click **Settings** (top right)

3. In the left sidebar, go to:
   **Secrets and variables** → **Actions**

4. Click **New repository secret**

5. Add the secret:
   - **Name**: `PLAY_STORE_SERVICE_ACCOUNT_JSON`
   - **Value**: Paste from clipboard (Cmd+V)
   - Click **Add secret**

---

## ✅ Verification

To test if everything works:

1. Make sure you also have the keystore secrets set up
2. Create a test release:
   ```bash
   git tag v1.2.1-test
   git push origin v1.2.1-test
   ```

3. Go to GitHub → Actions tab

4. Watch the "Deploy to Google Play" workflow run

5. If successful, check Play Console → Internal testing
   - You should see a new release!

---

## ⚠️ Important Security Notes

1. **Never commit the JSON file to Git**
   - It's already in `.gitignore` by default
   - Delete the downloaded JSON after encoding to base64

2. **The JSON file contains sensitive credentials**
   - Anyone with this file can upload to your Play Store
   - Keep it secure!

3. **You can always revoke access**
   - Go back to Play Console → API access
   - Remove service account if needed

4. **Keep a backup**
   - Store the base64 version in a password manager
   - If you lose it, you'll need to create a new key

---

## 🐛 Troubleshooting

### "Permission denied" when uploading

**Solution**: Go back to Play Console → API access → Service accounts → Manage permissions
- Make sure the service account has "Manage production releases" checked

### "Service account not found"

**Solution**: Wait 5-10 minutes after creating the service account
- Google Cloud needs time to propagate the account

### "Invalid JSON"

**Solution**: Re-download the JSON key
- Make sure you copied the entire base64 string
- No extra spaces or line breaks

### Workflow fails with "Authentication failed"

**Solution**: 
1. Verify the secret name is exactly: `PLAY_STORE_SERVICE_ACCOUNT_JSON`
2. Re-encode the JSON and update the secret
3. Make sure you granted the right permissions in Play Console

---

## 📸 Visual Summary

```
Google Play Console
    ↓
API Access
    ↓
Create/Link Google Cloud Project
    ↓
Create Service Account (in Google Cloud Console)
    ↓
Download JSON Key
    ↓
Grant Permissions (back in Play Console)
    ↓
Convert to Base64
    ↓
Add to GitHub Secrets
    ↓
✅ Ready for automated deployments!
```

---

## 🔗 Official Documentation

- [Google Play Developer API](https://developers.google.com/android-publisher)
- [Service Account Setup](https://developers.google.com/android-publisher/getting_started)
- [Play Console API Access](https://support.google.com/googleplay/android-developer/answer/9844283)

---

## 🎉 You're Done!

Once you have the `PLAY_STORE_SERVICE_ACCOUNT_JSON` secret added to GitHub:

✅ You can automatically deploy to Play Store
✅ Choose any track: internal, alpha, beta, production
✅ No more manual uploads!

**Test it:**
```bash
git tag v1.2.1
git push origin v1.2.1
```

Watch your app automatically upload to Play Store! 🚀

---

**Questions?** See `DEPLOYMENT_GUIDE.md` or check GitHub Actions logs for detailed errors.




