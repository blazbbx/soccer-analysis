# Use a lightweight official Python image
FROM python:3.10-slim

# Install FFmpeg (Crucial for the encoder worker!)
RUN apt-get update && \
    apt-get install -y ffmpeg && \
    rm -rf /var/lib/apt/lists/*

# Set the working directory inside the container
WORKDIR /app

# Copy requirements and install them
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy your Python scripts into the container
COPY . .

# We don't set a CMD here because docker-compose will specify which script to run