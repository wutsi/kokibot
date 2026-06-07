/**
 * File Upload Manager
 * Handles file uploads and displays uploaded files
 */
const FileUpload = {
    agentName: null,
    uploadButton: null,
    fileInput: null,
    filesContainer: null,
    uploadedFiles: [], // Array of {name, path, extension}

    init(agentName) {
        this.agentName = agentName;
        this.setupElements();
        this.setupEventListeners();
    },

    setupElements() {
        this.uploadButton = document.getElementById('upload-button');
        this.fileInput = document.getElementById('file-input');
        this.filesContainer = document.getElementById('uploaded-files-container');
    },

    setupEventListeners() {
        // Click upload button to open file picker
        this.uploadButton.addEventListener('click', () => {
            this.fileInput.click();
        });

        // Handle file selection
        this.fileInput.addEventListener('change', async (e) => {
            const files = Array.from(e.target.files);
            if (files.length > 0) {
                await this.uploadFiles(files);
            }
            // Reset file input
            e.target.value = '';
        });
    },

    async uploadFiles(files) {
        this.uploadButton.classList.add('uploading');

        for (const file of files) {
            try {
                await this.uploadFile(file);
            } catch (error) {
                console.error(`Error uploading ${file.name}:`, error);
                this.showError(`Failed to upload ${file.name}`);
            }
        }

        this.uploadButton.classList.remove('uploading');
    },

    async uploadFile(file) {
        const formData = new FormData();
        formData.append('file', file);

        const response = await fetch(`/upload?name=${encodeURIComponent(this.agentName)}`, {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            throw new Error(`Upload failed: ${response.status}`);
        }

        const data = await response.json();

        // Add to uploaded files list
        const fileInfo = {
            name: data.name,
            path: data.path,
            size: data.size,
            extension: this.getFileExtension(data.name)
        };

        this.uploadedFiles.push(fileInfo);
        this.renderUploadedFile(fileInfo);
    },

    renderUploadedFile(fileInfo) {
        const fileDiv = document.createElement('div');
        fileDiv.className = 'uploaded-file';
        fileDiv.dataset.path = fileInfo.path;

        const icon = document.createElement('span');
        icon.className = 'uploaded-file-extension file-extension-' + fileInfo.extension;
        icon.textContent = fileInfo.extension;

        const infoContainer = document.createElement('div');
        infoContainer.className = 'uploaded-file-info';

        const nameSpan = document.createElement('span');
        nameSpan.className = 'uploaded-file-name';
        nameSpan.textContent = fileInfo.name;
        nameSpan.title = fileInfo.name;

        const sizeSpan = document.createElement('span');
        sizeSpan.className = 'uploaded-file-size';
        sizeSpan.textContent = this.formatFileSize(fileInfo.size);

        infoContainer.appendChild(nameSpan);
        infoContainer.appendChild(sizeSpan);

        const removeBtn = document.createElement('button');
        removeBtn.className = 'uploaded-file-remove';
        removeBtn.innerHTML = '&times;';
        removeBtn.title = 'Remove file';
        removeBtn.addEventListener('click', () => {
            this.removeFile(fileInfo.path);
        });

        fileDiv.appendChild(icon);
        fileDiv.appendChild(infoContainer);
        fileDiv.appendChild(removeBtn);

        this.filesContainer.appendChild(fileDiv);
    },

    removeFile(path) {
        // Remove from array
        this.uploadedFiles = this.uploadedFiles.filter(f => f.path !== path);

        // Remove from DOM
        const fileDiv = this.filesContainer.querySelector(`[data-path="${path}"]`);
        if (fileDiv) {
            fileDiv.remove();
        }
    },

    getUploadedFilePaths() {
        return this.uploadedFiles.map(f => f.path);
    },

    getUploadedFilesInfo() {
        return this.uploadedFiles.map(f => ({
            name: f.name,
            path: f.path,
            size: f.size,
            extension: f.extension
        }));
    },

    clearUploadedFiles() {
        this.uploadedFiles = [];
        this.filesContainer.innerHTML = '';
    },

    getFileExtension(filename) {
        const parts = filename.split('.');
        return parts.length > 1 ? parts.pop().toLowerCase() : '';
    },

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
    },

    getFileIcon(extension) {
        // Map common extensions to emoji icons
        const iconMap = {
            // Documents
            'pdf': '📄',
            'doc': '📝',
            'docx': '📝',
            'txt': '📝',
            'md': '📝',

            // Images
            'jpg': '🖼️',
            'jpeg': '🖼️',
            'png': '🖼️',
            'gif': '🖼️',
            'svg': '🖼️',
            'webp': '🖼️',

            // Code
            'js': '📜',
            'ts': '📜',
            'py': '📜',
            'java': '📜',
            'kt': '📜',
            'cpp': '📜',
            'c': '📜',
            'html': '📜',
            'css': '📜',
            'json': '📜',
            'xml': '📜',
            'yaml': '📜',
            'yml': '📜',

            // Spreadsheets
            'xls': '📊',
            'xlsx': '📊',
            'csv': '📊',

            // Archives
            'zip': '📦',
            'rar': '📦',
            'tar': '📦',
            'gz': '📦',

            // Audio
            'mp3': '🎵',
            'wav': '🎵',
            'ogg': '🎵',

            // Video
            'mp4': '🎬',
            'avi': '🎬',
            'mov': '🎬',
            'mkv': '🎬'
        };

        return iconMap[extension] || '📎';
    },

    showError(message) {
        // You can integrate this with your existing error display system
        console.error(message);
        // TODO: Show error notification to user
    },

    setAgent(agentName) {
        this.agentName = agentName;
        this.clearUploadedFiles();
    }
};
