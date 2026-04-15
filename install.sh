#!/bin/sh

KOKIBOT_VERSION="0.0.6"
KOKIBOT_PORT=10807
BIN_DIR="$HOME/Applications/kokibot"
HOME_DIR="$HOME/.kokibot"
TMP_DIR="$HOME/tmp/$(uuidgen)/kokibot_install"
PLIST="$HOME/Library/LaunchAgents/com.kokibot.service.plist"

check_java() {
    if ! command -v java >/dev/null 2>&1; then
        echo "Error: Java is not installed. Please install Java 17 or newer."
        exit 1
    fi

    JAVA_FULL_VERSION=$(java -XshowSettings:properties -version 2>&1 | grep "java.runtime.version" | head -1 | awk '{print $3}')
    JAVA_MAJOR_VERSION=$(echo "$JAVA_FULL_VERSION" | cut -d. -f1)
    if [ "$JAVA_MAJOR_VERSION" -lt 17 ]; then
        echo "Error: Java 17 or newer is required. Found Java $JAVA_FULL_VERSION."
        exit 1
    fi
}

install_files() {
    # Create temporary directory for download and extraction
    mkdir -p "$TMP_DIR"
    cd "$TMP_DIR" || exit 1

    # Download the latest release of Kokibot
    echo "Downloading Kokibot v$KOKIBOT_VERSION..."
    curl -L "https://github.com/wutsi/kokibot/releases/download/v$KOKIBOT_VERSION/kokibot.zip" --output kokibot.zip  || exit 1

    # Unzip
    echo "Unpacking..."
    unzip -q kokibot.zip

    # Copy files to the appropriate locations
    echo "Installing files..."
    mkdir -p "$HOME_DIR"
    cp -Rn kokibot/home/* "$HOME_DIR/"
    mkdir -p "$BIN_DIR"
    cp kokibot/kokibot.jar "$BIN_DIR/"
}

install_service() {
    JAVA_EXEC=$(which java)

    OS=$(uname)
    if [ "$OS" = "Darwin" ]; then
        # macOS launchd
        cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.kokibot.service</string>
    <key>ProgramArguments</key>
    <array>
        <string>$JAVA_EXEC</string>
        <string>-jar</string>
        <string>--spring.profiles.active=prod</string>
        <string>$BIN_DIR/kokibot.jar</string>
        <string>-Dserver.port=$KOKIBOT_PORT</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>WorkingDirectory</key>
    <string>$BIN_DIR</string>
    <key>StandardOutPath</key>
    <string>$HOME/.kokibot/kokibot.log</string>
    <key>StandardErrorPath</key>
    <string>$HOME/.kokibot/kokibot.err</string>
</dict>
</plist>
EOF
        launchctl unload "$PLIST" 2>/dev/null
        launchctl load "$PLIST"
        echo "Kokibot service installed and started (macOS)."
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
StandardOutput=append:$HOME/.kokibot/kokibot.log
StandardError=append:$HOME/.kokibot/kokibot.err
Restart=always

[Install]
WantedBy=default.target
EOF
        systemctl --user daemon-reload
        systemctl --user stop kokibot.service 2>/dev/null
        systemctl --user enable kokibot.service
        systemctl --user start kokibot.service
        echo "Kokibot systemd user service installed and started (Linux)."
    else
        echo "Unsupported OS: $OS"
        exit 1
    fi
}

cleanup() {
    cd ~
    rm -rf "$TMP_DIR"
    echo "Installation complete in $HOME_DIR"
}

main() {
    check_java
    install_files
    #install_service
    #cleanup
}

main
