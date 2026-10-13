# AeroTrack - Flight Telemetry Monitoring

AeroTrack is a Java Swing project that simulates rocket telemetry and stores the data in a MySQL database. It also detects abnormal readings and uses SQL queries to analyze flight data.

## Technologies Used
- Java and Swing
- MySQL
- JDBC
- Maven

## Features
- Simulates rocket flights and generates telemetry packets.
- Stores flight and telemetry data in MySQL.
- Detects high temperature, low battery, and other abnormal readings.
- Displays flight history and telemetry graphs.
- Compares flights and exports data to CSV.
- Provides a database workspace to view records and analysis results.

## Database Tables
- `flights`: Stores flight details.
- `telemetry`: Stores telemetry readings.
- `events`: Stores detected events and anomalies.
- `app_users`: Stores application users and roles.

## Setup

**1.** Start MySQL Server and open MySQL Workbench.

**2.** Run the SQL files in the `sql` folder as needed:
- `01_setup_database.sql`
- `02_check_existing_schema.sql`
- `03_analysis_queries.sql`

**3.** Open the project folder containing `pom.xml` in VS Code.

**4.** Enter your MySQL password in the PowerShell terminal:

```powershell
$env:AEROTRACK_DB_PASSWORD = Read-Host "Enter MySQL password"
```

**5.** Compile the project:

```powershell
mvn clean compile
```

**6.** Run the application:

```powershell
mvn org.codehaus.mojo:exec-maven-plugin:3.6.4:java
```

## Demo Login
- Username: `admin`
- Password: `AeroTrack@123`

The demo account must exist in your database for these credentials to work.

## How It Works

The simulator generates telemetry packets. Java connects to MySQL using JDBC and stores the readings. The application then uses SQL queries to display flight history, graphs, events, and analysis reports.

## Project Structure

- `App.java`: Starts the application.
- `LoginDialog.java`: Handles login.
- `Dashboard.java`: Contains the main interface and tabs.
- `FlightSimulator.java`: Generates telemetry data.
- `TelemetryPacket.java`: Represents a telemetry reading.
- `AnomalyAnalyzer.java`: Checks for abnormal readings.
- `Database.java`: Handles database connections and SQL queries.
- `TelemetryChart.java`: Displays graphs.
- `UserAccount.java`: Stores user and role information.

## Note

This project uses simulated telemetry data and is intended for learning Java, JDBC, SQL, and DBMS concepts.