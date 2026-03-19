echo "Checking if keycloak_db exists..."
if ! psql -h postgres -U admin -d postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'keycloak_db'" | grep -q 1; then
    echo "Database not found. Creating keycloak_db..."
    psql -h postgres -U admin -d postgres -c "CREATE DATABASE keycloak_db;"
else
    echo "keycloak_db already exists. Skipping creation."
fi
echo "keycloak_db setup complete."