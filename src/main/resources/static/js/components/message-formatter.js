/**
 * Message text and metadata formatting
 * Handles escaping, file display, time formatting
 */
class MessageFormatter {
    /**
     * Escape HTML and preserve newlines
     */
    escapeAndPreserveNewlines(text) {
        if (!text) return '';

        const escaped = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');

        return escaped.replace(/\n/g, '<br>');
    }

    /**
     * Create files display element
     */
    createFilesDisplay(filesInfo) {
        const filesDiv = document.createElement('div');
        filesDiv.className = 'message-files';

        filesInfo.forEach(fileInfo => {
            const fileDiv = this.createFileElement(fileInfo);
            filesDiv.appendChild(fileDiv);
        });

        return filesDiv;
    }

    /**
     * Create single file element
     */
    createFileElement(fileInfo) {
        const fileDiv = document.createElement('div');
        fileDiv.className = 'message-file';

        const icon = document.createElement('span');
        icon.className = 'message-file-extension file-extension-' + fileInfo.extension;
        icon.textContent = fileInfo.extension;

        const infoContainer = document.createElement('div');
        infoContainer.className = 'message-file-info';

        const nameSpan = document.createElement('span');
        nameSpan.className = 'message-file-name';
        nameSpan.textContent = fileInfo.name;
        nameSpan.title = fileInfo.name;

        const sizeSpan = document.createElement('span');
        sizeSpan.className = 'message-file-size';
        sizeSpan.textContent = this.formatFileSize(fileInfo.size);

        infoContainer.appendChild(nameSpan);
        infoContainer.appendChild(sizeSpan);

        fileDiv.appendChild(icon);
        fileDiv.appendChild(infoContainer);

        return fileDiv;
    }

    /**
     * Format file size (bytes to KB/MB/GB)
     */
    formatFileSize(bytes) {
        if (bytes === 0 || bytes === null || bytes === undefined) {
            return '0 B';
        }

        const kb = bytes / 1024;
        const mb = kb / 1024;
        const gb = mb / 1024;

        if (gb >= 1) {
            return `${gb.toFixed(gb >= 10 ? 0 : 1)} GB`;
        } else if (mb >= 1) {
            return `${mb.toFixed(mb >= 10 ? 0 : 1)} MB`;
        } else if (kb >= 1) {
            return `${kb.toFixed(kb >= 10 ? 0 : 1)} KB`;
        } else {
            return `${bytes} B`;
        }
    }

    /**
     * Format timestamp
     */
    formatTime(date) {
        return date.toLocaleTimeString('en-US', {
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    /**
     * Format agent name (kebab-case to Title Case)
     */
    formatAgentName(name) {
        return name.split('-')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');
    }
}
