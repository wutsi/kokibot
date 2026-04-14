# Setup Guide

This guide provides detailed instructions for installing and configuring Kokibot on different platforms and environments.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

| Software | Minimum Version | Purpose | Installation |
|----------|----------------|---------|--------------|
| **Java** | 17 | Runtime environment | [Download OpenJDK](https://openjdk.org/) |
| **Maven** | 3.6+ | Build tool | [Download Maven](https://maven.apache.org/download.cgi) |
| **Git** | Any | Version control | [Download Git](https://git-scm.com/) |

### Optional Software

| Software | Purpose | Installation |
|----------|---------|--------------|
| **GraalVM** | Python tool support | [Download GraalVM](https://www.graalvm.org/downloads/) |

### API Keys & Credentials

You'll need the following credentials:

| Credential | Required For | How to Obtain |
|------------|--------------|---------------|
| `DEEPSEEK_API_KEY` | LLM functionality (required) | [Deepseek Platform](https://platform.deepseek.com/) |
| `TELEGRAM_TOKEN` | Telegram channel (optional) | [@BotFather on Telegram](https://t.me/botfather) |
| `MAIL_USERNAME` | Email tools (optional) | Your email provider |
| `MAIL_PASSWORD` | Email tools (optional) | Your email provider |
| `BRAVE_SEARCH_API_KEY` | Web search tool (optional) | [Brave Search API](https://brave.com/search/api/) |

---

## Installation

### Step 1: Clone the Repository

```bash
git clone https://github.com/wutsi/kokibot.git
cd kokibot
```

### Step 2: Verify Java Installation

```bash
java -version
```

Expected output:
```
openjdk version "17.0.x" or higher
```

If Java is not installed or version is below 17:

#### macOS
```bash
# Using Homebrew
brew install openjdk@17
```

#### Ubuntu/Debian
```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

#### Windows
Download and install from [OpenJDK](https://openjdk.org/) or [Adoptium](https://adoptium.net/).

### Step 3: Verify Maven Installation

```bash
mvn -version
```

Expected output:
```
Apache Maven 3.6.x or higher
```

If Maven is not installed:

#### macOS
```bash
brew install maven
```

#### Ubuntu/Debian
```bash
sudo apt update
sudo apt install maven
```

#### Windows
Download from [Maven Downloads](https://maven.apache.org/download.cgi) and add to PATH.

### Step 4: Build the Project

```bash
mvn clean install
```

This will:
- Compile Kotlin source code
- Run all tests
- Verify code coverage (93% minimum)
- Run ktlint code style checks
- Package the application

Expected output:
```
[INFO] BUILD SUCCESS
[INFO] Total time: XX.XXX s
```

💡 **Tip:** If the build fails due to test or coverage issues, check the error messages for specific failures.

---

## Configuration

### Step 1: Create Home Directory

```bash
mkdir -p ~/kokibot/config
mkdir -p ~/kokibot/workspace/history
mkdir -p ~/kokibot/workspace/memory
mkdir -p ~/kokibot/skills
```

### Step 2: Create Settings File

Create `~/kokibot/config/settings.json`:

```bash
cat > ~/kokibot/config/settings.json << 'EOF'
{
  "assistant": {
    "max-iterations": 10
  },
  "llm": {
    "type": "deepseek",
    "api-key": "${DEEPSEEK_API_KEY}",
    "model": "deepseek-chat"
  },
  "channels": [
    {
      "type": "telegram",
      "token": "${TELEGRAM_TOKEN}"
    }
  ],
  "memory": {
    "window": 3,
    "compaction-frequency": 6
  }
}
EOF
```

### Step 3: Set Environment Variables

Create a `.env` file or export directly:

```bash
# Required
export DEEPSEEK_API_KEY="your-deepseek-api-key"

# Optional - Telegram Channel
export TELEGRAM_TOKEN="your-telegram-bot-token"

# Optional - Email Tools
export MAIL_USERNAME="your-email@gmail.com"
export MAIL_PASSWORD="your-app-password"

# Optional - Web Search
export BRAVE_SEARCH_API_KEY="your-brave-api-key"
```

💡 **Tip:** For persistent environment variables, add these to your shell profile (`~/.bashrc`, `~/.zshrc`, etc.).

### Step 4: Configure Email (Optional)

If using email tools, add to `settings.json`:

```json
{
  "mail": {
    "smtp": {
      "host": "smtp.gmail.com",
      "port": 587,
      "username": "${MAIL_USERNAME}",
      "password": "${MAIL_PASSWORD}",
      "from": "your-email@gmail.com"
    },
    "imap": {
      "host": "imap.gmail.com",
      "port": 993,
      "username": "${MAIL_USERNAME}",
      "password": "${MAIL_PASSWORD}"
    }
  }
}
```

#### Gmail Setup

For Gmail, you need an **App Password**:

1. Enable 2-Factor Authentication on your Google Account
2. Go to [App Passwords](https://myaccount.google.com/apppasswords)
3. Generate a new app password for "Mail"
4. Use this password as `MAIL_PASSWORD`

### Step 5: Configure Web Search (Optional)

Add to `~/kokibot/config/tools/web_search.json`:

```json
{
  "api-key": "${BRAVE_SEARCH_API_KEY}"
}
```

### Step 6: Add System Instructions (Optional)

Create `~/kokibot/AGENT.md` with custom instructions:

```markdown
# System Instructions

You are Kokibot, a helpful AI assistant.

## Behavior

- Be concise and direct
- Use tools when appropriate
- Maintain context across conversations

## Style

- Professional but friendly
- Technical when needed
```

---

## Verification

### Step 1: Run the Application

```bash
mvn spring-boot:run
```

Expected output:
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

Kokibot started successfully
Telegram channel initialized
```

### Step 2: Test Telegram Bot (If Configured)

1. Open Telegram
2. Find your bot by username
3. Send `/help`
4. Verify bot responds with available commands

### Step 3: Test Built-in Tools

Send these test messages:

```
What's the current time?
```
Expected: Clock tool returns current date/time

```
/tool clock
```
Expected: Tool details displayed

```
/health
```
Expected: System health status

### Step 4: Verify Configuration

Check that all files exist:

```bash
ls -la ~/kokibot/config/settings.json
ls -la ~/kokibot/workspace/
```

### Step 5: Check Logs

Logs are output to console. Verify:
- ✅ No ERROR messages
- ✅ LLM provider initialized
- ✅ Tools registered
- ✅ Channels started

---

## Advanced Configuration

### Custom Home Directory

Override the default home directory:

```bash
mvn spring-boot:run -Duser.home=/custom/path/kokibot
```

Or set in `application.properties`:
```properties
user.home=/custom/path/kokibot
```

### Multiple Channels

Configure multiple channels in `settings.json`:

```json
{
  "channels": [
    {
      "type": "telegram",
      "token": "${TELEGRAM_TOKEN_1}"
    },
    {
      "type": "telegram",
      "token": "${TELEGRAM_TOKEN_2}"
    }
  ]
}
```

### Custom Tool Configuration

Create tool-specific config in `~/kokibot/config/tools/`:

**Example:** `~/kokibot/config/tools/shell.json`
```json
{
  "timeout": 10000,
  "allowed-commands": ["ls", "pwd", "echo"]
}
```

### Memory Configuration

Adjust memory behavior in `settings.json`:

```json
{
  "memory": {
    "window": 7,
    "compaction-frequency": 12
  }
}
```

- `window`: Days of history to include in compaction (default: 3)
- `compaction-frequency`: Hours between compaction runs (default: 6)

### Assistant Behavior

Configure iteration limits:

```json
{
  "assistant": {
    "max-iterations": 15
  }
}
```

⚠️ **Warning:** Higher iteration counts increase response time and API costs.

---

## Platform-Specific Instructions

### macOS

#### Install Dependencies
```bash
# Install Homebrew (if not installed)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install Java and Maven
brew install openjdk@17 maven

# Set JAVA_HOME
echo 'export JAVA_HOME=/usr/local/opt/openjdk@17' >> ~/.zshrc
source ~/.zshrc
```

#### Run as Background Service
```bash
# Create launch agent
mkdir -p ~/Library/LaunchAgents
cat > ~/Library/LaunchAgents/com.wutsi.kokibot.plist << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.wutsi.kokibot</string>
    <key>ProgramArguments</key>
    <array>
        <string>/usr/local/bin/mvn</string>
        <string>spring-boot:run</string>
    </array>
    <key>WorkingDirectory</key>
    <string>/path/to/kokibot</string>
    <key>RunAtLoad</key>
    <true/>
</dict>
</plist>
EOF

# Load service
launchctl load ~/Library/LaunchAgents/com.wutsi.kokibot.plist
```

---

### Linux (Ubuntu/Debian)

#### Install Dependencies
```bash
# Update package list
sudo apt update

# Install Java and Maven
sudo apt install openjdk-17-jdk maven git

# Verify installation
java -version
mvn -version
```

#### Run as systemd Service
```bash
# Create service file
sudo nano /etc/systemd/system/kokibot.service
```

Add content:
```ini
[Unit]
Description=Kokibot AI Assistant
After=network.target

[Service]
Type=simple
User=your-username
WorkingDirectory=/home/your-username/kokibot
ExecStart=/usr/bin/mvn spring-boot:run
Restart=on-failure
Environment="DEEPSEEK_API_KEY=your-key"
Environment="TELEGRAM_TOKEN=your-token"

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl daemon-reload
sudo systemctl enable kokibot
sudo systemctl start kokibot
sudo systemctl status kokibot
```

---

### Windows

#### Install Dependencies
1. Download and install [OpenJDK 17](https://adoptium.net/)
2. Download and install [Maven](https://maven.apache.org/download.cgi)
3. Add Maven to PATH:
   - System Properties → Environment Variables
   - Add Maven `bin` directory to `Path`

#### Set Environment Variables
```cmd
setx DEEPSEEK_API_KEY "your-key"
setx TELEGRAM_TOKEN "your-token"
```

#### Run Application
```cmd
cd kokibot
mvn spring-boot:run
```

#### Run as Windows Service
Use [NSSM](https://nssm.cc/) to create a Windows service:

```cmd
nssm install Kokibot "C:\Program Files\Maven\bin\mvn.cmd" "spring-boot:run"
nssm set Kokibot AppDirectory C:\path\to\kokibot
nssm set Kokibot AppEnvironmentExtra DEEPSEEK_API_KEY=your-key
nssm start Kokibot
```

---

## Docker Deployment

### Dockerfile

Create `Dockerfile` in project root:

```dockerfile
FROM maven:3.9-eclipse-temurin-17

WORKDIR /app

# Copy project files
COPY pom.xml .
COPY src ./src

# Build application
RUN mvn clean install -DskipTests

# Create kokibot home directory
RUN mkdir -p /root/kokibot/config
RUN mkdir -p /root/kokibot/workspace

# Expose any ports if needed (not required for Telegram)
# EXPOSE 8080

CMD ["mvn", "spring-boot:run"]
```

### docker-compose.yml

```yaml
version: '3.8'

services:
  kokibot:
    build: .
    container_name: kokibot
    environment:
      - DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY}
      - TELEGRAM_TOKEN=${TELEGRAM_TOKEN}
      - MAIL_USERNAME=${MAIL_USERNAME}
      - MAIL_PASSWORD=${MAIL_PASSWORD}
    volumes:
      - ./config:/root/kokibot/config
      - ./workspace:/root/kokibot/workspace
      - ./skills:/root/kokibot/skills
    restart: unless-stopped
```

### Run with Docker

```bash
# Build image
docker build -t kokibot .

# Run container
docker run -d \
  --name kokibot \
  -e DEEPSEEK_API_KEY="your-key" \
  -e TELEGRAM_TOKEN="your-token" \
  -v $(pwd)/config:/root/kokibot/config \
  -v $(pwd)/workspace:/root/kokibot/workspace \
  kokibot

# Or use docker-compose
docker-compose up -d
```

---

## Troubleshooting

### Build Failures

#### Problem: Maven build fails with Java version error
```
[ERROR] Failed to execute goal: requires Java 17 or higher
```

**Solution:**
```bash
# Check Java version
java -version

# Install Java 17+ if needed
# On macOS: brew install openjdk@17
# On Ubuntu: sudo apt install openjdk-17-jdk

# Set JAVA_HOME
export JAVA_HOME=/path/to/java17
```

#### Problem: Test failures
```
[ERROR] Tests run: X, Failures: Y
```

**Solution:**
```bash
# Run tests with verbose output
mvn test -X

# Skip tests temporarily to verify build
mvn clean install -DskipTests
```

#### Problem: Coverage threshold not met
```
[ERROR] Coverage check failed: line coverage 0.XX < 0.93
```

**Solution:** This is expected if you're modifying the code. Either:
- Add tests to increase coverage
- Temporarily skip coverage check: `mvn install -Djacoco.skip=true`

---

### Runtime Errors

#### Problem: Application fails to start
```
ConfigurationException: settings.json not found
```

**Solution:**
```bash
# Verify settings file exists
ls ~/kokibot/config/settings.json

# Create if missing
mkdir -p ~/kokibot/config
# ... create settings.json
```

#### Problem: LLM API errors
```
ERROR: Failed to connect to Deepseek API
```

**Solution:**
```bash
# Verify API key is set
echo $DEEPSEEK_API_KEY

# Test API key manually
curl -H "Authorization: Bearer $DEEPSEEK_API_KEY" \
  https://api.deepseek.com/v1/models
```

#### Problem: Telegram bot not responding
```
ERROR: Unauthorized (401) - Invalid bot token
```

**Solution:**
1. Verify token is correct: `echo $TELEGRAM_TOKEN`
2. Test with Telegram API:
```bash
curl "https://api.telegram.org/bot$TELEGRAM_TOKEN/getMe"
```
3. Regenerate token via @BotFather if needed

---

### Email Tool Issues

#### Problem: Gmail authentication fails
```
ERROR: Authentication failed - Username and Password not accepted
```

**Solution:**
- Use **App Password**, not your regular Gmail password
- Enable 2FA on Google Account first
- Generate app password at: https://myaccount.google.com/apppasswords

#### Problem: IMAP connection timeout
```
ERROR: Connection timed out to imap.gmail.com:993
```

**Solution:**
- Check firewall allows outbound port 993
- Verify IMAP is enabled in Gmail settings
- Test connection: `telnet imap.gmail.com 993`

---

### Memory & History Issues

#### Problem: Memory compaction not running
```
No memory compaction logs visible
```

**Solution:**
Check configuration:
```json
{
  "memory": {
    "compaction-frequency": 6
  }
}
```
Default is 6 hours. Manually trigger with `/compact` command.

#### Problem: Chat history growing too large
```
Prompt size exceeds token limit
```

**Solution:**
- Clear old history: `/clear`
- Reduce memory window: `"memory": { "window": 1 }`
- Increase compaction frequency: `"compaction-frequency": 3`

---

### Tool Execution Issues

#### Problem: Python tool fails
```
ERROR: GraalVM polyglot context not available
```

**Solution:**
Install GraalVM:
```bash
# macOS
brew install --cask graalvm/tap/graalvm-ce-java17

# Ubuntu
wget https://github.com/graalvm/graalvm-ce-builds/releases/download/...
tar -xzf graalvm-ce-java17-linux-amd64-XX.X.X.tar.gz
export JAVA_HOME=/path/to/graalvm
```

#### Problem: Shell tool command blocked
```
ERROR: Command 'sudo apt update' not allowed
```

**Solution:** This is a security feature. Blocked commands:
- `sudo`, `su`
- `rm -rf`
- `chmod`, `chown`
- Output redirection to `/etc/`

For safe alternatives, use specific tools or skills.

---

## Next Steps

Now that Kokibot is installed and configured:

1. **Explore Tools:** Read [Tools Reference](references/tools.md)
2. **Learn Commands:** Check [Commands Reference](references/commands.md)
3. **Create Skills:** Follow [Skills Guide](references/skills.md)
4. **Understand Architecture:** Review [Architecture](ARCHITECTURE.md)

---

## Getting Help

If you encounter issues not covered here:

- **GitHub Issues:** [Report a bug](https://github.com/wutsi/kokibot/issues)
- **Discussions:** [Ask questions](https://github.com/wutsi/kokibot/discussions)
- **Documentation:** Review [AGENT.md](../AGENT.md) for developer details

---

[← Back to Documentation](README.md) | [Architecture →](ARCHITECTURE.md)
