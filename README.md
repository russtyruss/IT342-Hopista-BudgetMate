# IT342-Hopista-BudgetMate

Backend quick setup (recommended):
1) Copy backend/.env.properties.example to backend/.env.properties
2) Fill in your real DB and secret values in backend/.env.properties
3) Run Spring Boot from the backend folder

Why startup failed with localhost:5432:
If DB_URL is missing, Spring previously used localhost fallback. This now requires DB_URL so misconfiguration is obvious.

Run backend (DB from environment variables, no hardcoding):
DB_URL='jdbc:postgresql://aws-1-ap-northeast-2.pooler.supabase.com:6543/postgres?sslmode=require' DB_USERNAME='postgres.fpobhuumnwnzhnivyhzp' DB_PASSWORD='<your-db-password>' MAVEN_OPTS='-Xms64m -Xmx192m -XX:MaxMetaspaceSize=128m' mvn -DskipTests spring-boot:run -Dspring-boot.run.jvmArguments='-Xms96m -Xmx320m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -Dspring.devtools.restart.enabled=false -Dspring.devtools.livereload.enabled=false'

If you use OAuth/email/storage, set these too before running:
JWT_SECRET='<long-random-secret>'
JWT_EXPIRATION_MS='2592000000'
GOOGLE_CLIENT_ID='<google-client-id>'
GOOGLE_CLIENT_SECRET='<google-client-secret>'
MAIL_USERNAME='<smtp-username>'
MAIL_PASSWORD='<smtp-password>'
SUPABASE_SERVICE_ROLE_KEY='<supabase-service-role-key>'

Optional: persist env vars in your shell profile (~/.zshrc)
export DB_URL='jdbc:postgresql://aws-1-ap-northeast-2.pooler.supabase.com:6543/postgres?sslmode=require'
export DB_USERNAME='postgres.fpobhuumnwnzhnivyhzp'
export DB_PASSWORD='<your-db-password>'
export JWT_SECRET='<long-random-secret>'
export JWT_EXPIRATION_MS='2592000000'
export GOOGLE_CLIENT_ID='<google-client-id>'
export GOOGLE_CLIENT_SECRET='<google-client-secret>'
export MAIL_USERNAME='<smtp-username>'
export MAIL_PASSWORD='<smtp-password>'
export SUPABASE_SERVICE_ROLE_KEY='<supabase-service-role-key>'

run mobile avd: emulator -avd Pixel_4
run install app: cd /Users/russjiehopista/Documents/IT342-Hopista-BudgetMate/mobile
./gradlew installDebug

How to run it in VS Code:

Open Command Palette (Cmd+Shift+P)
Run Tasks: Run Task
Choose Android: Start Pixel_4 + Install Debug
