---
name: markitdown
description: |
    Convert files and office documents to Markdown.
    Supports PDF, DOCX, PPTX, XLSX, images (with OCR), audio (with transcription), HTML, CSV, JSON, XML, ZIP, YouTube URLs, EPubs and more.
requires:
    bin:
        - markitdown
        - python3
metadata:
    github: https://github.com/microsoft/markitdown
---

# Skill: MarkItDown

MarkItDown is a Python tool developed by Microsoft for converting various file formats to Markdown. It's particularly
useful for converting documents into LLM-friendly text format, as Markdown is token-efficient and well-understood by
modern language models.

## Key Benefits

- Convert documents to clean, structured Markdown
- Token-efficient format for LLM processing
- Supports 15+ file formats
- Optional AI-enhanced image descriptions
- OCR for images and scanned documents
- Speech transcription for audio files

## When to Use

**Use markitdown for:**

- Fetching documentation (README, API docs)
- Converting web pages to markdown
- Document analysis (PDFs, Word, PowerPoint)
- YouTube transcripts
- Image text extraction (OCR)
- Audio transcription

## Quick Start

```bash
# Convert file to markdown
markitdown document.pdf -o output.md

# Convert URL
markitdown https://example.com/docs -o docs.md
```

## Supported Formats

- PDF
- DOCX
- PPTX
- XLSX
- HTML
- CSV
- Audio (MP3, WAV, etc.)
- Images (with OCR)
- YouTube URLs
- EPubs
- ZIP archives (extract and convert contents)
- JSON
- XML

## Installation

The skill requires Microsoft's `markitdown` CLI:

```bash
pip install 'markitdown[all]'
```

Or install specific formats only:

```bash
pip install 'markitdown[pdf,docx,pptx]'
```

## Common Patterns

### Fetch Documentation

```bash
markitdown https://github.com/user/repo/blob/main/README.md -o readme.md
```

### Convert PDF

```bash
markitdown document.pdf -o document.md
```

## Python API

```python
from markitdown import MarkItDown

md = MarkItDown()
result = md.convert("document.pdf")
print(result.text_content)
```

## Troubleshooting

### "markitdown not found"

```bash
pip install 'markitdown[all]'
```

### OCR Not Working

```bash
# Ubuntu/Debian
sudo apt-get install tesseract-ocr

# macOS
brew install tesseract
```
