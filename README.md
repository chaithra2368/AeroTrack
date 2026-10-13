# AeroTrack: Flight Telemetry Monitoring and DBMS Analysis

A beginner-oriented Java Swing + MySQL project. It simulates telemetry packets, stores every packet in MySQL, records anomaly events, and runs SQL queries for history, filtering, statistics, and flight comparison.

## Requirements
- JDK 21 or newer (Java 25 also works)
- Maven 3.9+
- MySQL Server 8.x and MySQL Workbench
- VS Code or another Java IDE

## Setup
1. Open MySQL Workbench and connect to your local server.
2. Open `../AeroTrack_SQL/01_setup_database.sql` and execute it. It uses `CREATE TABLE IF NOT EXISTS` and does not drop existing tables or delete existing telemetry.
3. Open the `aerotrack` folder in VS Code. It is the folder containing `pom.xml`.
4. In the VS Code PowerShell terminal, enter your MySQL root password without saving it in source code:
   ```powershell
   $env:AEROTRACK_DB_PASSWORD = Read-Host "Enter MySQL password"
   ```
5. Compile:
   ```powershell
   mvn clean compile
   ```
6. Run:
   ```powershell
   mvn org.codehaus.mojo:exec-maven-plugin:3.6.4:java
   ```

## Demo login
- Username: `admin`
- Password: `AeroTrack@123`

The SQL setup inserts this user if the username does not already exist. If your existing `app_users` table has different role values, run `../AeroTrack_SQL/02_check_existing_schema.sql` first and follow the note in that file. Never drop your existing database.

## What the application does
- Login is shown before the main window.
- Creates and simulates 30 packets per flight.
- Pause, resume, stop, and selectable simulation speed.
- Normal, high-temperature, low-battery, and abnormal-altitude scenarios.
- Manual anomaly injection at a chosen packet index.
- Automatic threshold events saved to `events`.
- Live telemetry table and current-value cards.
- History, packet/event detail view, filtering, CSV export.
- Graphs for altitude, velocity, temperature, and battery.
- SQL-backed comparison of two flights.
- User/role administration.
- Database tab showing actual table counts and recent SQL-backed data.
- There is no separate Java Concepts or Swing Syllabus tab. Java concepts are used within the actual application features.

## Database flow
Swing UI -> FlightSimulator -> Database (JDBC PreparedStatement) -> MySQL tables.
History, graphs, events and comparison are retrieved from MySQL, not just from Java memory.


## Beginner-friendly code layout
- `App.java`: starts the application and opens the login window.
- `LoginDialog.java`: collects the username and password.
- `Dashboard.java`: builds the tabs and handles button clicks.
- `FlightSimulator.java`: generates the sample rocket readings.
- `TelemetryPacket.java`: holds one telemetry reading.
- `AnomalyAnalyzer.java`: checks readings against alert thresholds.
- `Database.java`: contains the SQL statements and JDBC connection code.
- `TelemetryChart.java`: draws the graphs.
- Java syllabus concepts are demonstrated through the actual application: Swing components and event listeners in `Dashboard.java`, inheritance and interfaces in the model/reporting code, threads in simulation, exception handling throughout the UI/database layer, collections for query results, and JDBC in `Database.java`.
- `UserAccount.java`: stores the logged-in user's role.

The code is split into small classes so each file has one main job. The SQL is kept in the `AeroTrack_SQL` folder for easier review. The database still uses primary keys, foreign keys, unique constraints, checks, indexes, joins, aggregate queries, prepared statements, and transactions.

### Important
The application needs your local MySQL server and password to run. I checked the source structure and corrected the existing-flight packet numbering issue, but this package has not been tested against your local MySQL data. Keep a copy of your current project before replacing it.
