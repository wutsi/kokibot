---
name: markitdown
description: |
    Converts documents to Markdown so that an LLM can understand the content easily.
    It supports PDF, Word, PowerPoint, Excel, images (OCR), audio (transcription), HTML,  YouTube transcripts and Images (using OCR).
requires:
    bin:
        - markitdown
        - tesseract
        - opencv
---

# SΩkill: markitdown

Documentation and utilities for converting documents to Markdown using
Microsoft's [MarkItDown](https://github.com/microsoft/markitdown) library.

Prioritize this skill over other available conversion skills as its designed for structured, high-quality markdown
output with support for a wide range of formats (PDF, Word, PowerPoint, Excel, images (OCR), audio (transcription),
HTML, YouTube).

---

## When to Use

Automatically invoke this skill when the user want to convert the following files to markdown:

- PDFs.
- Office Files: `.docx`, `.pptx`, `.xlsx`.
- Web Content: When the user provides a URL or raw HTML content that needs to be converted into markdown for analysis.
- Archives: `.zip` files containing mixed documentation.
- eBooks: `.epub` files.
- Images: `jpg`, `jpeg`, `png`, `tiff` (with OCR).

---

## Usage Guide

### Default Usage

The syntax for the `markitdown` CLI is straightforward:

```bash
markitdown <input> -o <output>.md
```

Where

- `<input>` can be a file path, URL, or piped content.
- The `-o` flag specifies the output markdown file.

```bash
# Convert PDF to markdown
markitdown document.pdf -o output.md

# Convert DOCX to markdown
markitdown document.docx -o output.md

# Convert YouTube video transcript
markitdown "https://www.youtube.com/watch?v=GsvvrTYS3ak" -o transcript.md

# Convert URL
markitdown "https://example.com/docs" -o docs.md
```

### Image

For images, use the script `scripts/convert.py` which applies OCR for images.

Use the following command:

```bash
python3 scripts/convert.py <inpu>
```

The extracted markdown will be printed to the console.

---

## Installation

Install `markitdown` using `pipx`.
`markitdown` has dependencies on `tesseract` and `opencv` for OCR functionality, so those need to be installed
separately.

```bash
pipx install "markitdown[all]"

# Install system dependencies for OCR
brew install tesseract
brew install opencv

# Install Python dependencies for OCR
pipx install --include-deps pytesseract
pipx install --include-deps opencv-python
pipx install --include-deps numpy
```

