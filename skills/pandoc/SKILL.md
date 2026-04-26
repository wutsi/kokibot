---
name: pandoc
description: |
    This skill should be used when converting documents between formats (Markdown, DOCX, PDF, HTML, LaTeX, etc.) using pandoc.
    Use for format conversion, document generation, and preparing markdown for Google Docs or other word processors.
requires:
    bin:
        - pandoc
---

# Pandoc Document Conversion Skill

Convert documents between formats using pandoc, the universal document converter.

---

## When to Use

Automatically invoke this skill when the user:

- Mentions "convert to PDF", "generate PDF", "export to Word/DOCX"
- Asks about "pandoc", "markdown to PDF", "document conversion"
- Shows markdown with YAML frontmatter
- Asks about citations, bibliographies, academic papers
- Requests help with presentations (Beamer, reveal.js)
- Mentions LaTeX, XeLaTeX, or PDF engines
- Has errors converting documents

**NOTE**

- For conversion to `.md`, prefer `markitdown` for better structured output.

---

## Usage Guide

The syntax for the `markitdown` CLI is straightforward:

```bash
pandoc <input> [options] -o <output>.md
```

Where

- `<input>` can be a file path, URL, or piped content.
- The `-o` flag specifies the output markdown file.

### File Conversions Examples

```bash
# Basic conversion
pandoc input.md -o output.docx

# With table of contents
pandoc input.md --toc -o output.docx

# With custom reference doc (for styling)
pandoc input.md --reference-doc=template.docx -o output.docx

# Standalone with metadata
pandoc input.md -s --metadata title="Document Title" -o output.docx
```

### Conversion to PDF

For PDF output, specify the PDF engine (e.g., `tectonic`, `pdflatex`, `xelatex`).

```bash
pandoc input.md --pdf-engine=tectonic -o output.pdf
```

---

## Installation

```bash
brew install pandoc
brew install tectonic
```
