# Material Files — fork de Victor-root

[English](README.md) · **Français**

[![Dernière version](https://img.shields.io/github/v/release/Victor-root/MaterialFiles)](https://github.com/Victor-root/MaterialFiles/releases) [![Licence : GPL v3](https://img.shields.io/github/license/Victor-root/MaterialFiles?color=blue)](LICENSE)

Un fork personnel et amélioré de **[Material Files](https://github.com/zhanghai/MaterialFiles)** de Hai Zhang — un gestionnaire de fichiers Material Design open source pour Android.

> **Pourquoi ce fork ?**
> Material Files est déjà une excellente application — ceci n'est **pas** un concurrent ni une critique de l'original. Je l'ai forké pour avoir une version adaptée à **mon usage quotidien** : surtout des améliorations de cohérence de l'interface, des corrections de bugs, et un vrai support **Android TV**. Tout repose sur le travail de l'auteur d'origine, à qui revient tout le mérite de l'application.

Le dépôt d'origine reste la référence ; les sections ci-dessous décrivent **uniquement ce que ce fork ajoute ou modifie**. Pour ce qu'est fondamentalement l'application, voir [À propos de l'application d'origine](#à-propos-de-lapplication-dorigine).

## Ce que ce fork change

### 📺 Support Android TV
- Navigation complète à la télécommande (D-pad) — la barre latérale, la barre d'outils et les listes sont accessibles et correctement surlignées.
- L'onboarding du premier lancement fonctionne sur TV.
- Correction du blocage dans un dossier vide (le focus revient à la barre d'outils) et d'un saut de focus lors de l'activation du serveur FTP.

### 🚀 Onboarding au premier lancement
- Un écran d'accueil qui demande en amont les autorisations nécessaires (accès à tous les fichiers, notifications, installation d'APK), pour ne rien avoir à accorder en cours d'usage.

### ⬆️ Mises à jour intégrées
- Vérifie les *releases* GitHub de ce dépôt et peut télécharger et installer une nouvelle APK directement depuis **À propos → Rechercher des mises à jour** — sans passer par un store.

### 💾 Stockage amovible (carte SD / USB), repensé
- Les cartes SD et clés USB **apparaissent automatiquement sous « Stockage interne »** et se rafraîchissent au branchement — fini le « Ajouter un support » manuel via SAF.
- **Renommage** d'un volume et choix de son **icône** ; USB ou SD détecté automatiquement (avec forçage manuel possible).
- Un disque multi-partitions est regroupé sous un seul en-tête **« Clé USB » / « Carte SD »**, avec ses partitions imbriquées en **« Partition 1, 2, … »**.
- Raccourci **Éjecter**, et l'espace libre se met à jour en direct (par ex. juste après une suppression) au lieu d'attendre un redémarrage.

### 🎯 Applications par défaut par type de fichier
- Nouveau **Réglages → Applications par défaut** : choisissez quelle app ouvre les images, l'audio, les vidéos, les PDF et le texte — pour que ces fichiers s'ouvrent directement avec la bonne app, sans demander à chaque fois.

### 🎨 Cohérence de l'interface et thème
- Les icônes de la barre d'état s'adaptent à la couleur de l'en-tête / du tiroir (claire ou foncée), corrigeant les cas illisibles blanc-sur-blanc et noir-sur-foncé, avec un fondu à l'ouverture du tiroir.
- Barre d'application foncée en mode nuit au lieu de la teinte primaire claire.
- Icônes affichées dans les menus ⋮ ; popup thémé en mode sombre.
- Corrections des couleurs Material You (M3) en mode sombre et du curseur des interrupteurs.
- Les noms longs passent à la ligne au lieu d'être coupés par « … ».
- Stockage interne renommé avec une icône de téléphone ; un vrai ruban marque les dossiers en marque-page ; icône du serveur FTP rafraîchie.

### 🐛 Autres corrections et finitions
- Message clair quand un dossier ne peut pas être ouvert, au lieu d'un échec silencieux.
- Sauvegarde cloud / extraction de données désactivées pour la confidentialité.
- Toutes les chaînes propres au fork traduites en **31 langues**.
- Identifiant d'application changé en `fr.vroot.android.files`, pour qu'il s'installe à côté de la version Play/F-Droid sans entrer en conflit.

## À propos de l'application d'origine

Material Files est un gestionnaire de fichiers Material Design open source pour Android 6.0+ :

- Material Design soigné et navigation par fil d'Ariane.
- Support root ; voir, extraire et créer les archives courantes ; FTP, SFTP, SMB et WebDAV.
- Couleurs personnalisables et mode nuit (avec noir intégral optionnel).
- Compatible Linux — liens symboliques, permissions et contexte SELinux — via de vrais appels système plutôt qu'en analysant `ls`, bâti sur l'API de fichiers Java NIO2 avec `ViewModel` / `LiveData`.

## Compilation

Ouvrez le projet dans Android Studio et lancez-le, ou en ligne de commande :

```sh
./gradlew assembleRelease
```

Le code natif est compilé pour toutes les ABI (arm64-v8a, armeabi-v7a, x86, x86_64), produisant une APK universelle unique qui fonctionne sur tous les appareils.

## Crédits et licence

- **Application d'origine et toutes ses fonctionnalités :** [Hai Zhang](https://github.com/zhanghai) et les contributeurs.
- **Ce fork :** [Victor-root](https://github.com/Victor-root).

Distribué sous **licence publique générale GNU v3.0**, la même que l'original. Voir [LICENSE](LICENSE).

```
Copyright (C) 2018 Hai Zhang
Copyright (C) 2024 Victor-root (fork modifications)

This program is free software: you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the Free
Software Foundation, either version 3 of the License, or (at your option)
any later version. It is distributed WITHOUT ANY WARRANTY; see the GNU
General Public License for more details.
```
