# Skill: markitdown

Convert files and office documents to Markdown.

[![](https://img.shields.io/badge/github-repo-blue?logo=github)](https://github.com/microsoft/markitdown)
[![Python](https://img.shields.io/badge/python-3.x-blue.svg?logo=phython)](https://python.org)

## What This Skill Provides

- ✅ Documentation for using MarkItDown
- ✅ A batch conversion script (`scripts/batch_convert.py`)
- ✅ Usage examples and API reference

The actual document conversion is done by Microsoft's `markitdown` CLI, installed separately via pip.

## Setup

Convert files and office documents to Markdown.
Supports PDF, DOCX, PPTX, XLSX, images (with OCR), audio (with transcription), HTML, CSV, JSON, XML, ZIP, YouTube URLs,
EPubs and more.

### Install Pandoc

``` bash
# Install with all features
pipx install 'markitdown[all]'
```

## Quick Start

```bash
# Convert PDF
markitdown document.pdf -o output.md

# Fetch web docs
markitdown https://example.com/docs -o docs.md

# Batch convert
python ~/.openclaw/skills/markitdown/scripts/batch_convert.py docs/*.pdf -o markdown/
```

## Supported Formats

| Format       | Features                |
|--------------|-------------------------|
| PDF          | Text extraction         |
| Word (.docx) | Headings, lists, tables |
| PowerPoint   | Slides, text            |
| Excel        | Tables, sheets          |
| Images       | OCR + metadata          |
| Audio        | Speech transcription    |
| HTML         | Structure preservation  |
| YouTube      | Video transcription     |

## Documentation

- [SKILL.md](SKILL.md) - Main documentation
- [USAGE-GUIDE.md](USAGE-GUIDE.md) - Detailed examples
- [reference.md](reference.md) - Full API reference
- [POST_INSTALL.md](POST_INSTALL.md) - Setup guide
