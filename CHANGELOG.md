# Changelog

Toutes les modifications notables de ce fork de **Material Files** sont
documentées ici. Ce fork est basé sur [Material Files](https://github.com/zhanghai/MaterialFiles)
de Hai Zhang ; cette première version regroupe l'ensemble du travail réalisé
depuis le départ du fork.

Le format s'inspire de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).

## [1.8.0] – 2026-06-11

### Intégration système & sélecteur de fichiers (SAF)
- **Fournisseur de documents (DocumentsProvider)** : Material Files peut
  désormais servir de source dans le sélecteur du système. Une autre appli peut
  choisir un **dossier** (`OPEN_DOCUMENT_TREE`) ou un **fichier**
  (`OPEN_DOCUMENT` / `GET_CONTENT`) sur **n'importe quel back-end** : stockage
  local, SMB, FTP, SFTP, WebDAV, archives.
- Les fichiers et dossiers sélectionnés renvoient de vraies URI de
  document/arborescence avec **permissions persistantes** (l'appli destinataire
  conserve l'accès après redémarrage).

### Performances & confidentialité
- **Suppression complète de Firebase** (Analytics + Crashlytics) et de toute
  dépendance aux services Google : démarrage à froid **~5× plus rapide** (plus
  de chargement de WebView/Chromium ni d'initialisation de télémétrie au
  lancement). **Aucune donnée n'est envoyée à Google.**
- Sauvegarde automatique Android et extraction de données vers le cloud
  désactivées.

### Stockage & volumes amovibles
- Détection automatique des cartes SD / clés USB (via `UsbManager`) avec icône
  adaptée, au branchement comme à la reprise de l'appli.
- Noms lisibles pour les volumes amovibles ; numérotation des volumes de même
  nom (« Partition N », « Clé USB (Partition N) »).
- Regroupement et indentation des partitions d'un même disque sous un en-tête
  parent dans le menu latéral.
- Raccourci d'éjection des volumes amovibles.
- Stockage interne renommé, avec une icône de téléphone.
- Sous-titres d'espace libre rafraîchis à l'ouverture du tiroir et après les
  opérations sur les fichiers.

### Navigation & thème
- Refonte du menu latéral : en-tête coloré, fond neutre, bouton d'action (FAB)
  thémé, élément sélectionné à la couleur du thème.
- Barre d'outils principale colorée à la couleur primaire du thème.
- Icônes ajoutées au menu « … » (kebab) et aux menus affichage / tri.
- Sélecteur de couleur activé avec Material Design 3 ; ruban de marque-page pour
  les dossiers favoris.
- Icône de serveur FTP repensée (Tabler `server-2`).
- Corrections du mode sombre Material Design 3 (couleurs vives, interrupteurs
  cohérents) ; barre d'application sombre en mode nuit.

### Barres système (statut & navigation)
- Teinte des icônes de la barre de statut calculée d'après la luminance de
  l'en-tête (et non plus seulement le mode clair/sombre).
- Couleur de la barre de navigation synchronisée avec la barre d'application.
- Transition (fondu) symétrique de la teinte à l'**ouverture comme à la
  fermeture** du tiroir.
- Correction des icônes blanc-sur-blanc quand le tiroir est ouvert.

### Liste de fichiers & opérations
- Mode sélecteur clairement signalé : icône de fermeture + nom de l'appli
  demandeuse dans le titre.
- Bouton de progression des opérations de fichiers intégré, avec popover de
  détails.
- Titre de la barre d'outils défilant (marquee) pour lire les noms longs.
- Le retour arrière quitte à la racine au lieu d'ouvrir le menu latéral.
- Texte long mis à la ligne au lieu d'être tronqué.
- Message clair lorsqu'un dossier ne peut pas être ouvert.

### Android TV
- Prise en charge d'Android TV (navigation à la télécommande, onboarding dédié).
- Corrections de focus : ne plus rester bloqué dans un dossier vide ; gestion de
  l'interrupteur du serveur FTP.

### Premier lancement
- Flux d'autorisations au premier lancement (onboarding).

### Paramètres
- Choix de l'application par défaut **par catégorie de fichier** (ouverture sans
  redemander à chaque fois).

### Réseau & SMB
- Option de **chiffrement SMB par serveur**.
- Bibliothèque WebDAV (dav4jvm) embarquée dans un dépôt Maven hors-ligne au lieu
  de JitPack (build plus fiable).

### Identité du fork & traductions
- Rebranding : `applicationId` `fr.vroot.vfiles`, nom de lanceur
  localisé, icônes, écran « À propos » (Victor-root mainteneur, Hai Zhang
  crédité comme auteur original).
- README réécrit (anglais + français avec sélecteur de langue) ; ancien README
  chinois retiré.
- Traduction des ~55 chaînes propres au fork dans une trentaine de langues.

### Build & infrastructure
- Les builds debug sont signés avec la clé release lorsque `signing.properties`
  est présent (pour tester les mises à jour in-app).
- Correction d'un crash en mode paysage sur grand écran (tablette).

---

Basé sur Material Files de Hai Zhang, sous licence GPL-3.0.
