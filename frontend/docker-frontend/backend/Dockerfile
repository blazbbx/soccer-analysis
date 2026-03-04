# Use a lightweight Java runtime (Change to 17 or 21 depending on your project!)
FROM eclipse-temurin:21-jre-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy the jar file from your target folder to the container
# (The * ensures it grabs the jar regardless of the exact version number)
COPY *.jar app.jar 
# Ez igy nemjo majd at kell irni
# Expose port 8080 so the React frontend can reach it
EXPOSE 8080

# The command to start the application
ENTRYPOINT ["java", "-jar", "app.jar"]