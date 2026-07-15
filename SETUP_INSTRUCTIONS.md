# 🚀 Setup Panel URL untuk AsepGanteng

## Update URL Panel Server

Semua URL panel sudah di-centralize di `Config.kt` di setiap role folder.

### Cara Update:

1. **Owner App** (`owner/app/src/main/java/com/asepganteng/owner/Config.kt`):
   ```kotlin
   const val PANEL_BASE_URL = "http://your-panel-server.com:3000"
   ```
   
2. **Reseller App** (`reseller/app/src/main/java/com/asepganteng/reseller/Config.kt`):
   - Same Config.kt structure, auto-updated dengan PANEL_BASE_URL

3. **Anonymous App** (`anonymous/app/src/main/java/com/asepganteng/anonymous/Config.kt`):
   - Same Config.kt structure

4. **Pemilik App** (`pemilik/app/src/main/java/com/asepganteng/pemilik/Config.kt`):
   - Same Config.kt structure

### Contoh Setup:

**Development (localhost):**
```kotlin
const val PANEL_BASE_URL = "http://192.168.1.100:3000"
```

**Production (domain):**
```kotlin
const val PANEL_BASE_URL = "https://panel.asepxyz.com"
```

### Build & Deploy:

Setelah update `PANEL_BASE_URL`, build APK untuk setiap role:

```bash
cd owner && ./gradlew assembleRelease
cd ../reseller && ./gradlew assembleRelease
cd ../anonymous && ./gradlew assembleRelease
cd ../pemilik && ./gradlew assembleRelease
```

### Note:

- Kalau pakai `http://`, pastikan web panel set `MIXED_CONTENT_ALWAYS_ALLOW` (sudah enabled)
- Kalau pakai `https://`, pastikan SSL certificate valid (atau gunakan self-signed dengan config khusus)
- Endpoint path (`/owner`, `/reseller`, dll) sudah fixed di Config.kt - jangan perlu ubah ulang

---

**Last Updated:** Auto-fixed dari repository lama (szxennofficial.my.id)  
**Status:** Ready untuk push ke GitHub & rebuild dengan URL server barumu
