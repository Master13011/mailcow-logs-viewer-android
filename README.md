# mailcow Logs Viewer – application Android

Client Android (WebView durcie) pour [mailcow Logs Viewer](https://github.com/ShlomiPorush/mailcow-logs-viewer).

L'application agit comme un client mobile : elle se connecte au serveur Mailcow configuré par l'utilisateur et permet de consulter son interface depuis un environnement Android optimisé.

Elle apporte notamment des fonctionnalités natives supplémentaires pour améliorer le confort d'utilisation et sécuriser l'accès au serveur.

## Fonctionnalités

🌐 Connexion à un serveur existant : configuration personnalisée de l'adresse HTTPS du backend **mailcow-logs-viewer**.

🔐 Protection biométrique : verrouillage de l'application par empreinte digitale ou code PIN de l'appareil.

⏱️ Verrouillage automatique : protection après une minute d'inactivité ou de mise en arrière-plan.

🔄 Actualisation rapide : rechargement de l'interface par glissement vers le bas.

📥 Téléchargement de fichiers : récupération des fichiers proposés par l'interface du serveur.

🔒 Connexion sécurisée HTTPS : refus des connexions HTTP non sécurisées et des certificats SSL invalides.

⚙️ Configuration intégrée : possibilité de changer de serveur et de gérer les paramètres de sécurité.

## Premier lancement
Saisissez l'adresse **HTTPS** de votre instance (ex. `https://logs.exemple.com`).

Pour la changer : Paramètres de l'application → *Changer de serveur*.

## Sécurité

La sécurité est au cœur du projet :

- Communication HTTPS uniquement.
- Vérification stricte des certificats SSL.
- Authentification biométrique native Android.
- Verrouillage automatique lorsque l'application reste inactive.
- Isolation des sessions lors du changement de serveur.
- Désactivation de l'accès aux fichiers locaux et de la géolocalisation dans la WebView.
- Compatible session par cookie, OAuth2/OIDC (navigation HTTPS conservée dans la WebView), Basic Auth,
  import de fichiers (CSV) et export CSV (téléchargement natif).

Dependabot configuré pour suivre les mises à jour (Gradle + GitHub Actions).

## Architecture

Technologies utilisées :

- Kotlin
- Android SDK
- Android WebView
- AndroidX Biometric
- AndroidX AppCompat

