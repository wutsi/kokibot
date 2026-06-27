#!/bin/sh

KOKIBOT_VERSION="0.0.57"
KOKIBOT_PORT=10807
BIN_DIR="$HOME/Applications/kokibot"
HOME_DIR="$HOME/.kokibot"
TMP_DIR="$HOME/tmp/kokibot-installer/$(uuidgen)"
PLIST_DIR="$HOME/Library/LaunchAgents"
PLIST="$PLIST_DIR/com.kokibot.service.plist"

check_java() {
    if ! command -v java >/dev/null 2>&1; then
        echo "Error: Java is not installed. Please install Java manually (Java 17 or newer)."
        exit 1
    fi

    JAVA_FULL_VERSION=$(java -XshowSettings:properties -version 2>&1 | grep "java.runtime.version" | head -1 | awk '{print $3}')
    JAVA_MAJOR_VERSION=$(echo "$JAVA_FULL_VERSION" | cut -d. -f1)
    if [ "$JAVA_MAJOR_VERSION" -lt 17 ]; then
        echo "Error: Java 17 or newer is required. Found Java $JAVA_FULL_VERSION."
        exit 1
    fi
}

install_python(){
    echo "INSTALLING PYTHON"

    # Python
    if ! command -v python3 >/dev/null 2>&1; then
        echo "Installing python..."
        brew install python
    fi

    # pipx
    if ! command -v pipx >/dev/null 2>&1; then
        echo "Installing pipx..."
        brew install pipx
        pipx ensurepath
    fi
}

install_files() {
    echo "INSTALLING FILES"

    # Create temporary directory for download and extraction
    mkdir -p "$TMP_DIR"
    cd "$TMP_DIR" || exit 1

    # Download the latest release of Kokibot
    echo "Downloading kokibot v$KOKIBOT_VERSION..."
    curl -L "https://github.com/wutsi/kokibot/releases/download/v$KOKIBOT_VERSION/kokibot.zip" --output kokibot.zip  || exit 1

    # Unzip
    echo "Unpacking..."
    unzip -q kokibot.zip

    # Install files
    if [ ! -d "$HOME_DIR/agents" ]; then
        mkdir -p "$HOME_DIR/agents/koki"
        cp -R kokibot/* "$HOME_DIR/agents/koki" 2>/dev/null || true

        rm "$HOME_DIR/agents/koki/uninstall.sh" 2>/dev/null || true
        rm "$HOME_DIR/agents/koki/kokibot.jar" 2>/dev/null || true
    fi

    # Binaries
    mkdir -p "$BIN_DIR"
    mv kokibot/kokibot.jar "$BIN_DIR/"
    mv kokibot/uninstall.sh "$BIN_DIR/"
    chmod +x "$BIN_DIR/uninstall.sh"
}

install_service() {
    echo "INSTALLING SERVICE"

    JAVA_EXEC=$(which java)

    echo "Setting up kokibot service..."

    OS=$(uname)
    if [ "$OS" = "Darwin" ]; then
        # macOS launchd
        cat > "$BIN_DIR/start_service.sh" <<EOF
#!/bin/zsh
# Source your profile to get all exports and paths
PROFILES=("$HOME/.zprofile" "$HOME/.zshrc" "$HOME/.profile")

# Loop through and source them if they exist
for profile in \$PROFILES; do
    if [ -f "\$profile" ]; then
        source "\$profile"
    fi
done

$JAVA_EXEC -Dserver.port=$KOKIBOT_PORT -jar $BIN_DIR/kokibot.jar  --spring.profiles.active=prod
EOF

        chmod +x "$BIN_DIR/start_service.sh"

        if [ ! -d "$PLIST_DIR" ]; then
          mkdir -p "$PLIST_DIR"
        fi
        cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.kokibot.service</string>
    <key>ProgramArguments</key>
    <array>
        <string>/usr/bin/caffeinate</string>
        <string>-i</string>
        <string>-m</string>
        <string>$BIN_DIR/start_service.sh</string>
    </array>

    <key>RunAtLoad</key>
    <true/>

    <key>KeepAlive</key>
    <true/>

    <key>ProcessType</key>
    <string>Interactive</string>

    <key>WorkingDirectory</key>
    <string>$BIN_DIR</string>

    <key>StandardOutPath</key>
    <string>$HOME/.kokibot/logs/kokibot.log</string>

    <key>StandardErrorPath</key>
    <string>$HOME/.kokibot/logs/kokibot.err</string>
</dict>
</plist>
EOF
        launchctl unload "$PLIST" 2>/dev/null
        launchctl load "$PLIST"
        if [ $? -ne 0 ]; then
            echo "Error: Failed to start kokibot service"
            exit 1
        fi
    elif [ "$OS" = "Linux" ]; then
        # Linux systemd user service
        mkdir -p "$HOME/.config/systemd/user"
        SERVICE_FILE="$HOME/.config/systemd/user/kokibot.service"
        cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=Kokibot Service

[Service]
Type=simple
WorkingDirectory=$BIN_DIR
ExecStart=$JAVA_EXEC -Dserver.port=$KOKIBOT_PORT -jar $BIN_DIR/kokibot.jar  --spring.profiles.active=prod
StandardOutput=append:$HOME/.kokibot/logs/kokibot.log
StandardError=append:$HOME/.kokibot/logs/kokibot.err
Restart=always

[Install]
WantedBy=default.target
EOF
        systemctl --user daemon-reload
        systemctl --user stop kokibot.service 2>/dev/null
        systemctl --user enable kokibot.service
        systemctl --user start kokibot.service
        if [ $? -ne 0 ]; then
            echo "Error: Failed to start kokibot service"
            exit 1
        fi
    else
        echo "Unsupported OS: $OS"
        exit 1
    fi
}

cleanup() {
    cd ~ || exit 1
    rm -rf "$TMP_DIR"
}

main() {
    echo "Installing kokibot v$KOKIBOT_VERSION"
    check_java
    install_python
    install_files
    install_service
    cleanup
    echo "kokibot v$KOKIBOT_VERSION installed successfully."
}

main
