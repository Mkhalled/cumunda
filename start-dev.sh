#!/bin/bash

# Script pour démarrer l'environnement de développement
echo "🚀 Démarrage de l'environnement de développement..."

# Vérifier si Docker est installé
if ! command -v docker &> /dev/null; then
    echo "❌ Docker n'est pas installé. Veuillez installer Docker d'abord."
    exit 1
fi

# Vérifier si Docker Compose est installé
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose n'est pas installé. Veuillez installer Docker Compose d'abord."
    exit 1
fi

echo "📦 Démarrage de PostgreSQL avec Docker Compose..."
docker-compose up -d

# Attendre que PostgreSQL soit prêt
echo "⏳ Attente du démarrage de PostgreSQL..."
sleep 10

# Vérifier que PostgreSQL est accessible
until docker-compose exec -T postgres pg_isready -U orchestrator_user -d orchestrator_db; do
    echo "⏳ PostgreSQL n'est pas encore prêt. Attente..."
    sleep 5
done

echo "✅ PostgreSQL est prêt!"

# Construire le projet Maven
echo "🔨 Construction du projet Maven..."
./mvnw clean compile

if [ $? -eq 0 ]; then
    echo "✅ Projet compilé avec succès!"
    
    echo "🧪 Exécution des tests..."
    ./mvnw test
    
    if [ $? -eq 0 ]; then
        echo "✅ Tests réussis!"
        
        echo "🚀 Démarrage de l'application..."
        echo "📊 Camunda Cockpit sera disponible sur: http://localhost:8080"
        echo "🗄️  pgAdmin sera disponible sur: http://localhost:5050"
        echo "📡 API REST sera disponible sur: http://localhost:8080/api/workflow"
        echo ""
        echo "Credentials Camunda: admin/admin"
        echo "Credentials pgAdmin: admin@company.com/admin123"
        echo ""
        
        ./mvnw spring-boot:run
    else
        echo "❌ Échec des tests. Vérifiez les erreurs ci-dessus."
        exit 1
    fi
else
    echo "❌ Échec de la compilation. Vérifiez les erreurs ci-dessus."
    exit 1
fi