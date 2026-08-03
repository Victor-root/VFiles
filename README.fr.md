# VFiles

[English](README.md) · **Français**

[![Dernière version](https://img.shields.io/github/v/release/Victor-root/VFiles?style=for-the-badge&logo=github&label=release)](https://github.com/Victor-root/VFiles/releases)
[![Dernière mise à jour](https://img.shields.io/github/last-commit/Victor-root/VFiles/main?style=for-the-badge&logo=git&label=last%20update)](https://github.com/Victor-root/VFiles/commits/main)
[![Android](https://img.shields.io/badge/Android-APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Victor-root/VFiles/releases/latest)
[![Android TV](https://img.shields.io/badge/Android%20TV-supported-3DDC84?style=for-the-badge&logo=androidtv&logoColor=white)](https://github.com/Victor-root/VFiles/releases/latest)

[<img src="docs/badges/get-it-on-omnify.svg" alt="Disponible sur Omnify" height="56">](https://github.com/Victor-root/Omnify)

Un fork personnel et amélioré de **[Material Files](https://github.com/zhanghai/MaterialFiles)** de Hai Zhang, un gestionnaire de fichiers Material Design open source pour Android.

Le dépôt d'origine reste la référence ; les sections ci-dessous décrivent **uniquement ce que ce fork ajoute ou modifie**. Pour ce qu'est fondamentalement l'application, voir [À propos de l'application d'origine](#à-propos-de-lapplication-dorigine).

## ✨ Ce que ce fork change

### 📺 Support Android TV
- Navigation complète à la télécommande (D-pad) : la barre latérale, la barre d'outils et les listes sont accessibles et correctement surlignées.
- L'onboarding du premier lancement fonctionne sur TV.
- Correction du blocage dans un dossier vide (le focus revient à la barre d'outils) et d'un saut de focus lors de l'activation du serveur FTP.

### 🚀 Onboarding au premier lancement
- Un écran d'accueil qui demande en amont les autorisations nécessaires (accès à tous les fichiers, notifications), pour ne rien avoir à accorder en cours d'usage.

### 💾 Stockage amovible (carte SD / USB), repensé
- Les cartes SD et clés USB **apparaissent automatiquement sous « Stockage interne »** et se rafraîchissent au branchement. Fini le « Ajouter un support » manuel via SAF.
- **Renommage** d'un volume et choix de son **icône** ; USB ou SD détecté automatiquement (avec forçage manuel possible).
- Un disque multi-partitions est regroupé sous un seul en-tête **« Clé USB » / « Carte SD »**, avec ses partitions imbriquées en **« Partition 1, 2, … »**.
- Raccourci **Éjecter**, et l'espace libre se met à jour en direct (par ex. juste après une suppression) au lieu d'attendre un redémarrage.

### 🎯 Applications par défaut par type de fichier
- Nouveau **Réglages → Applications par défaut** : choisissez quelle app ouvre les images, l'audio, les vidéos, les PDF et le texte, pour que ces fichiers s'ouvrent directement avec la bonne app, sans demander à chaque fois.

### 📋 Progression des opérations sur les fichiers
- Un bouton de progression apparaît dans la barre d'outils pendant les copies, déplacements et opérations d'archive, ouvrant une popup avec le détail en direct de chaque fichier, plutôt qu'une simple notification.

### 🔒 Confidentialité et performance
- **Firebase (Analytics + Crashlytics) entièrement supprimé.** Rien concernant votre usage ou les plantages n'est envoyé où que ce soit, et l'application démarre aussi plus vite sans lui.
- Sauvegarde cloud et extraction de données désactivées, pour qu'Android n'envoie jamais les données de l'app hors de l'appareil.

### 🎨 Cohérence de l'interface et thème
- Les icônes de la barre d'état s'adaptent à la couleur de l'en-tête / du tiroir (claire ou foncée), corrigeant les cas illisibles blanc-sur-blanc et noir-sur-foncé, avec un fondu à l'ouverture du tiroir.
- Barre d'application foncée en mode nuit au lieu de la teinte primaire claire.
- Icônes affichées dans les menus ⋮ ; popup thémé en mode sombre.
- Tiroir de navigation repensé et thémé pour Material You (M3), avec corrections des couleurs en mode sombre et du curseur des interrupteurs.
- Les noms longs passent à la ligne au lieu d'être coupés par « … ».
- Stockage interne renommé avec une icône de téléphone ; un vrai ruban marque les dossiers en marque-page ; icône du serveur FTP rafraîchie.
- **Bord à bord** optionnel (Réglages → Interface → « Bord à bord ») : le contenu défile derrière la barre d'état et la barre de navigation au lieu de s'arrêter au-dessus. Désactivé par défaut.

### 🐛 Autres corrections et finitions
- Message clair quand un dossier ne peut pas être ouvert, au lieu d'un échec silencieux.
- **Chiffrement** optionnel par serveur pour les partages SMB.
- Correction d'un plantage sur tablette en mode paysage, et les barres de statut/navigation sont désormais colorées pour correspondre à la barre d'application.
- Retour arrière quitte directement à la racine au lieu de rester bloqué, et les titres longs de la barre d'outils défilent désormais.
- Toutes les chaînes propres au fork traduites en **31 langues**.
- Identifiant d'application changé en `fr.vroot.vfiles`, pour qu'il s'installe à côté de la version Play/F-Droid sans entrer en conflit.

## ⬆️ Mettre VFiles à jour

Il n'y a pas de système de mise à jour intégré ni de fiche sur un store. Pour mettre à jour : ouvrez la page des [releases](https://github.com/Victor-root/VFiles/releases), téléchargez la dernière APK, et installez-la par-dessus l'installation actuelle. Elle est signée avec la même clé à chaque fois, donc vos fichiers et réglages sont conservés. L'application qui ouvre le fichier téléchargé (navigateur, gestionnaire de fichiers) demandera, la première fois, l'autorisation d'installer des applications inconnues.

## 🗂️ Faire de VFiles le sélecteur de fichiers par défaut du système

VFiles expose **tous les back-ends qu'il gère** via le sélecteur de fichiers Android, pas seulement le stockage local : les partages SMB, FTP, SFTP et WebDAV, ainsi que le contenu des archives, apparaissent comme de simples dossiers dans la boîte de dialogue « Ouvrir » ou « Enregistrer » de n'importe quelle app, exactement comme un dossier local.

Android achemine les intents `ACTION_OPEN_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE` et `ACTION_GET_CONTENT` vers **Google DocumentsUI** (`com.google.android.documentsui`), même si VFiles est installé en tant que DocumentsProvider. Désactiver cette application système fait basculer Android vers VFiles comme sélecteur pour toutes les apps de l'appareil. Sans root, uniquement via ADB (débogage USB).

### Désactiver (faire de VFiles le sélecteur par défaut)

```sh
# Désactiver l'application DocumentsUI principale
adb shell pm disable-user --user 0 com.google.android.documentsui

# Désactiver son module overlay (présent sur certaines ROM ; ignorer l'erreur s'il est absent)
adb shell pm disable-user --user 0 com.google.android.overlay.modules.documentsui
```

Après ces commandes, toute application qui ouvre un sélecteur de fichier ou de dossier utilisera VFiles.

### Réactiver (remettre le sélecteur Google)

```sh
adb shell pm enable com.google.android.documentsui
adb shell pm enable com.google.android.overlay.modules.documentsui
```

### Notes

- **Sans root** : `pm disable-user --user 0` s'exécute via une connexion ADB standard (débogage USB).
- Testé sur **LineageOS** et d'autres ROM basées sur AOSP. Sur les ROM constructeur stock, le nom du module overlay peut différer ou être absent ; la commande `pm disable-user` sur le paquet principal est suffisante dans ce cas.
- Le changement survit aux redémarrages, mais est **par utilisateur** (affecte uniquement l'utilisateur actif lors de l'exécution ADB, en général l'utilisateur 0).
- Si une autre application avec un DocumentsProvider est installée, Android peut afficher un sélecteur. N'installez qu'un seul fournisseur si vous voulez éviter toute invite.

### 🔧 Pour les créateurs de ROM : en faire le vrai sélecteur système

Une variante de build séparée et optionnelle, `systemPicker`, permet à un créateur de ROM de remplacer purement et simplement DocumentsUI : signé avec la clé plateforme de la ROM, VFiles renvoie exactement les mêmes URI de stockage que DocumentsUI, si bien que même les apps qui les exigent spécifiquement (pas seulement celles qui utilisent le sélecteur générique ci-dessus) fonctionnent avec lui. Voir [`docs/systempicker-integration.md`](docs/systempicker-integration.md) (en anglais) pour les prérequis et les étapes d'intégration. Cela n'affecte en rien une installation normale.

---

## À propos de l'application d'origine ℹ️

Material Files est un gestionnaire de fichiers Material Design open source pour Android 6.0+ :

- Material Design soigné et navigation par fil d'Ariane.
- Support root ; voir, extraire et créer les archives courantes ; FTP, SFTP, SMB et WebDAV.
- Couleurs personnalisables et mode nuit (avec noir intégral optionnel).
- Compatible Linux (liens symboliques, permissions et contexte SELinux) via de vrais appels système plutôt qu'en analysant `ls`, bâti sur l'API de fichiers Java NIO2 avec `ViewModel` / `LiveData`.

## 💭 Pourquoi ce fork ?

Material Files est déjà une excellente application. Ceci n'est **pas** un concurrent ni une critique de l'original. Je l'ai forké pour avoir une version adaptée à **mon usage quotidien** : surtout des améliorations de cohérence de l'interface, des corrections de bugs, et un vrai support **Android TV**. Tout ce qui précède repose sur le travail de l'auteur d'origine, à qui revient tout le mérite de l'application.

## 🙏 Crédits et licence

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
