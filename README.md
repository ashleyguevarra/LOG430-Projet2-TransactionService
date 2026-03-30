# LOG430-Projet2-TransactionService

Service Spring Boot (transactions) pour le projet `LOG430-Projet2`. Structure initiale : paquets Java, MySQL via Docker Compose, aligné sur KeyService.

## Prérequis

- Java 21 (JDK)
- Docker (pour MySQL)

## Configuration locale

Copier `.env.exemple` vers `.env` et ajuster si besoin. Le port MySQL par défaut est **3307** pour éviter un conflit avec KeyService (3306).

## Lancer l’application

```bash
./mvnw clean spring-boot:run
```

- API : `http://localhost:8081/` (port choisi pour coexister avec KeyService sur 8080)

MySQL est démarré via `compose.yaml` grâce à `spring-boot-docker-compose` au lancement.

## Observabilité (OpenTelemetry / Actuator)

- **Santé / liveness** : `GET http://localhost:8081/actuator/health`
- **Métriques (Prometheus)** : `GET http://localhost:8081/actuator/prometheus`
- **Traces OTLP** : envoie vers `http://localhost:4318/v1/traces` par défaut. Avec `docker compose up`, le service **Jaeger** démarre aussi ; UI : `http://localhost:16686` (chercher le service `transaction`).
- Pour surcharger l’URL OTLP : variable `MANAGEMENT_OTLP_TRACING_ENDPOINT` (URL complète, ex. `http://jaeger:4318/v1/traces` si l’app tourne dans le même réseau Docker).

Les logs console incluent `traceId` et `spanId` (Micrometer) pour corréler avec Jaeger.

## Tests

```bash
./mvnw test
```

## Dépôt GitHub et collaborateurs

1. Créer un dépôt (ex. `transactionService` ou `LOG430-Projet2-TransactionService`) sur l’organisation ou le compte du cours.
2. **Settings → Collaborators** (ou **Manage access**) : inviter les membres de l’équipe avec leurs comptes GitHub.
3. Pousser ce dossier : `git remote add origin <url>` puis `git push -u origin main`.

Si la CLI GitHub (`gh`) est installée et connectée : `gh repo create` permet de créer le dépôt depuis la racine du projet.
