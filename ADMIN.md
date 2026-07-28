# Fluxio - Guide d'Administration 🛡️

Ce document présente le fonctionnement et la structure du système d'administration de **Fluxio**, en particulier la gestion et le suivi des abonnements ainsi que la création de notifications ciblées.

---

## 📋 Table des Matières
1. [Aperçu de la Section Admin](#-aperçu-de-la-section-admin)
2. [Suivi des Abonnements](#-suivi-des-abonnements)
3. [Création de Notifications](#-création-de-notifications)
4. [Structure des Données Firestore](#-structure-des-données-firestore)
5. [Guide d'Utilisation](#-guide-dutilisation)

---

## 🔍 Aperçu de la Section Admin

L'interface d'administration de Fluxio permet aux administrateurs de superviser l'activité de l'application, de suivre les nouveaux abonnements et d'envoyer des notifications ciblées ou globales. 

Toutes les fonctionnalités majeures d'administration sont implémentées dans le composant :
`SignalementsSupportView` (`app/src/main/java/com/fluxio/features/admin/Signalements_Support_View.kt`).

---

## 📈 Suivi des Abonnements

Accessible via le bouton de notification (icône de cloche 🔔) dans l'en-tête de la vue d'administration, le **Suivi des Abonnements** offre une vue d'ensemble immersive :

*   **Design Premium Sombre :** Utilisation d'un fond noir profond immersif (`0xFF0F0F0F`) avec des cartes élégantes à bordures subtiles (`0xFF161616`).
*   **Barre de Recherche Dynamique :** Permet de filtrer en temps réel par titre, message, nom d'utilisateur, numéro de téléphone, référence de transaction ou identifiant utilisateur.
*   **Identité Visuelle Claire :** Chaque notification affiche un avatar circulaire coloré selon le type d'événement (Vert pour un "Nouvel abonnement", Rouge/Gris pour les autres types) facilitant le scan visuel.
*   **Détails Rapides (Chips) :** Affichage compact et stylisé du numéro de téléphone et de la référence du paiement.

---

## ✉️ Création de Notifications

L'interface de création de notifications a été entièrement repensée pour une expérience utilisateur moderne et fluide.

### 👥 Ciblage des Destinataires
*   **Sélecteurs épurés sans couleur de fond :** Les deux options ("Tous les utilisateurs" et "Cibler des personnes @") sont intégrées harmonieusement.
*   **Indicateur d'état actif :**
    *   **Actif :** Texte blanc en gras souligné par une ligne rouge distinctive.
    *   **Inactif :** Texte gris simple sans fioritures.
*   **Alignement :** Les deux liens de sélection sont parfaitement centrés horizontalement au milieu de l'écran.

### 📥 Actions et Alignement
*   **Boutons superposés :** Pour optimiser l'espace et guider naturellement l'action sur mobile, le bouton **Envoyer** (rouge vif) est positionné verticalement au-dessus du bouton **Annuler** (contour blanc).

---

## 🗄️ Structure des Données Firestore

Les notifications d'administration et de suivi sont stockées dans la collection **Firebase Firestore** suivante :

### Collection : `subscription_notifications`

Chaque document de la collection contient la structure suivante :

| Champ | Type | Description |
| :--- | :--- | :--- |
| `title` | `String` | Titre de la notification (ex: "Nouvel abonnement") |
| `message` | `String` | Contenu textuel détaillé de la notification |
| `name` | `String` | Nom de l'utilisateur concerné |
| `phone` | `String` | Numéro de téléphone de l'utilisateur |
| `ref` | `String` | Référence de la transaction ou de l'abonnement |
| `userId` | `String` | ID de l'utilisateur cible |
| `createdAt` | `Long` | Timestamp de création en millisecondes |
| `timestamp` | `Long` | Timestamp alternatif pour la compatibilité temporelle |

---

## 🚀 Guide d'Utilisation

### Envoyer une notification globale
1. Ouvrez le **Suivi des Abonnements** (Icône Cloche 🔔).
2. Cliquez sur **Créer une Notification** (Bouton d'action flottant rouge en bas à droite).
3. Assurez-vous que **Tous les utilisateurs** est actif (souligné de rouge).
4. Remplissez le **Titre** et le **Message**.
5. Appuyez sur **Envoyer**.

### Envoyer une notification ciblée à un utilisateur
1. Dans l'écran de création, sélectionnez **Cibler des personnes (@)** (le lien sera souligné en rouge).
2. Recherchez et cochez le ou les utilisateurs concernés dans la liste qui s'affiche.
3. Saisissez le **Titre** et le **Message**.
4. Cliquez sur le bouton principal **Envoyer** pour enregistrer les notifications ciblées dans Firestore.
