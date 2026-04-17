#!/bin/sh

BIN_DIR="$HOME/Applications/kokibot"
HOME_DIR="$HOME/.kokibot"
PLIST="$HOME/Library/LaunchAgents/com.kokibot.service.plist"

uninstall_files() {
    echo "Deleting kokibot files..."

    rm -Rf "$BIN_DIR"
    rm -Rf "$HOME_DIR"
}

uninstall_service() {
    echo "Stopping and disabling kokibot service..."

    OS=$(uname)
    if [ "$OS" = "Darwin" ]; then
        launchctl unload "$PLIST" 2>/dev/null
    elif [ "$OS" = "Linux" ]; then
        systemctl --user stop kokibot.service 2>/dev/null
        systemctl --user disable kokibot.service
    else
        echo "Unsupported OS: $OS"
        exit 1
    fi
}

main() {
    uninstall_service
    uninstall_files
    echo "kokibot uninstalled successfully."
}

main
