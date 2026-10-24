🌸 FlowTrack – Menstrual Health Awareness Application

PRJ No.: 518 | SDG 3 – Good Health and Well-being

FlowTrack is a menstrual health awareness application designed to help users understand their menstrual cycles, track period history and symptoms, and access educational information about menstrual health and hygiene.

The application aims to improve menstrual health awareness through cycle tracking, rule-based period prediction, symptom monitoring, and personalized cycle insights.

✨ Features

🩸 Menstrual Cycle Tracking: Record and maintain menstrual period history.

📅 Period Prediction: Estimate upcoming periods using previously recorded cycle information and conditional rules.

🌙 Menstrual Phase Information: Provide information about menstrual, follicular, ovulation, and luteal phases.

📝 Symptom Tracking: Record symptoms and monitor changes across cycles.

📊 Cycle Analysis: Display cycle-related insights based on recorded information.

📚 Health Awareness: Provide educational content about menstrual health, hygiene, and self-care.

😌 Stress Awareness: Encourage users to understand the relationship between stress and menstrual health.

🎯 Objectives

Promote awareness and understanding of menstrual health.

Help users maintain a record of their menstrual cycles.

Provide estimated period dates based on available cycle history.

Encourage healthy menstrual hygiene practices.

Make menstrual health information accessible through a user-friendly application.

🛠️ Technology Stack

The project uses web technologies for the user interface and application logic, with a database for storing and retrieving information.

Update this section to match the technologies used in the final implementation.

Frontend: HTML, CSS, JavaScript

Backend: Python with Flask / Java (use the one actually implemented)

Database: SQL / MySQL (use the database actually implemented)

Prediction Logic: Rule-based conditional logic using recorded cycle data

Development Tools: Visual Studio Code, web browser, and relevant database tools

⚙️ How It Works

User Input: The user enters menstrual period dates and relevant cycle information.

Data Storage: The application records the available information for future reference.

Cycle Calculation: The system calculates cycle length using recorded period history.

Period Prediction: Conditional rules estimate the next period date using the available cycle data and average cycle length.

Symptom Monitoring: Users record symptoms and review their history.

Health Awareness: The application presents educational resources and information related to menstrual health.

Results: Users can view their recorded history and estimated cycle information through the application interface.

🧮 Period Prediction Method

FlowTrack uses a rule-based approach to estimate upcoming menstrual periods.

The general calculation is:

Predicted Next Period = Last Period Start Date + Average Cycle Length

The average cycle length can be calculated from the intervals between consecutive period start dates, when sufficient history is available.

Conditional statements (if-else) are used to handle different situations, such as when previous cycle data is available or when a default value is required.

Note: Period predictions are estimates, not medical diagnoses. Cycle lengths and ovulation dates can vary between individuals and from month to month.
