# mailcow Logs Viewer – application Android

Client Android (WebView durcie) pour [mailcow Logs Viewer](https://github.com/ShlomiPorush/mailcow-logs-viewer).

L'app affiche **votre instance existante** : aucune modification du backend n'est nécessaire.

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
