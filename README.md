# mailcow Logs Viewer – application Android

Client Android (WebView durcie) pour [mailcow Logs Viewer](https://github.com/ShlomiPorush/mailcow-logs-viewer).
L'app affiche **votre instance existante** : aucune modification du backend n'est nécessaire.

## Compilation (le plus simple : GitHub Actions)
1. Poussez ce dossier dans un dépôt GitHub.
2. Onglet **Actions → Build APK → Run workflow**.
3. Téléchargez l'APK dans les *Artifacts* du run.

Sans secrets, le workflow produit un APK **debug** (installable directement). Pour un APK **release signé**,
créez une clé puis ajoutez 4 secrets de dépôt :

```bash
keytool -genkeypair -v -keystore release.jks -alias mailcow -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks   # → ANDROID_KEYSTORE_BASE64
```
Secrets : `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
Conservez `release.jks` : la même clé est nécessaire pour les mises à jour.

## Compilation locale
Android Studio (dernière version stable) : *Open* sur ce dossier, puis *Build → Build APK(s)*.
En ligne de commande : `gradle wrapper --gradle-version 9.6.0` puis `./gradlew assembleDebug`.

## Premier lancement
Saisissez l'adresse **HTTPS** de votre instance (ex. `https://logs.exemple.com`).
Pour la changer : appui long sur l'icône de l'app → *Changer de serveur*.

## Choix techniques / sécurité
- AGP 9.2.1, Gradle 9.6.0, JDK 21, compileSdk 37, targetSdk 36, minSdk 26 (Android 8+).
- Seulement 2 dépendances AndroidX (appcompat 1.8.0, swiperefreshlayout 1.2.0), aucune bibliothèque tierce.
- HTTPS obligatoire (trafic en clair interdit), erreurs de certificat bloquées, pas d'accès fichiers,
  aucun bridge JavaScript, Safe Browsing activé, sauvegardes Android désactivées, R8 activé en release.
- Compatible session par cookie, OAuth2/OIDC (navigation HTTPS conservée dans la WebView), Basic Auth,
  import de fichiers (CSV) et export CSV (téléchargement natif).
- Dependabot configuré pour suivre les mises à jour (Gradle + GitHub Actions).
