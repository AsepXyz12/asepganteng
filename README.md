# AsepGanteng — 4 Role Apps

Repo ini isinya 4 project Android WebView terpisah, masing-masing buka
dashboard panel sesuai role login:

| Folder       | Package                     | URL yang dibuka                                              |
|--------------|------------------------------|----------------------------------------------------------------|
| `reseller/`  | com.asepganteng.reseller     | http://server.szxennofficial.my.id:3100/reseller               |
| `owner/`     | com.asepganteng.owner        | http://server.szxennofficial.my.id:3100/owner                  |
| `pemilik/`   | com.asepganteng.pemilik      | http://server.szxennofficial.my.id:3100/pemilik-x7fq2mz9wr3kd8 |
| `anonymous/` | com.asepganteng.anonymous    | http://server.szxennofficial.my.id:3100/anonymous-q9zk3xr7vb2mt5 |

## Cara dapetin APK (otomatis, tanpa Android Studio / tanpa setup SDK manual)

1. Push repo ini ke GitHub (lihat command di bawah).
2. Buka tab **Actions** di repo GitHub kamu (bisa dari HP, browser biasa).
3. Tunggu workflow **"Build 4 APK Apps"** jalan (otomatis kepicu tiap push ke
   branch `main`) — biasanya selesai 3-8 menit.
4. Klik run yang udah selesai (centang hijau) → scroll ke bawah ke bagian
   **Artifacts** → di situ ada 4 file zip:
   - `AsepGanteng-reseller-debug-apk`
   - `AsepGanteng-owner-debug-apk`
   - `AsepGanteng-pemilik-debug-apk`
   - `AsepGanteng-anonymous-debug-apk`
5. Download salah satu/semua, extract zip-nya (isinya `app-debug.apk`),
   install ke HP (aktifkan "Install from unknown sources" kalau diminta).

Kalau workflow gagal (tanda silang merah), buka log run itu buat lihat
error-nya di step "Build debug APK" — biasanya info error Gradle ada di
situ, tinggal share ke saya errornya biar dicariin fix-nya.

## Push dari Termux

```bash
cd ~/asepganteng-apps   # folder hasil extract repo ini

git init
git branch -M main
git config --global user.name "AsepXyz12"
git config --global user.email "emailkamu@gmail.com"

git add .
git status              # cek dulu, pastiin gak ada file .env/database ikut
git commit -m "4 role apps + auto build workflow"
git remote add origin https://github.com/AsepXyz12/asepganteng.git
git push -u origin main
```

Push token: pakai Personal Access Token GitHub (bukan password akun) pas
diminta, scope minimal `repo`.
